package br.com.cotapreco.service;

import br.com.cotapreco.dto.ComparacaoDtos.*;
import br.com.cotapreco.dto.PedidoMinimoDtos.PreviaManualPedidoMinimo;
import br.com.cotapreco.dto.PlanoCompraDtos.*;
import br.com.cotapreco.enums.*;
import br.com.cotapreco.exception.*;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import br.com.cotapreco.security.UsuarioAtualService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class PlanoCompraService {
    private final CotacaoRepository cotacoes;
    private final RespostaCotacaoRepository respostas;
    private final EscolhaCompraCotacaoRepository escolhas;
    private final UsuarioAtualService usuarioAtual;
    private final ComparacaoCotacaoService comparacao;
    private final EstadoPedidoCompraService estadoPedidos;
    private final HistoricoPlanoCompraService historico;

    @Transactional
    public VisaoComparacao atualizar(Long cotacaoId,SolicitacaoPlanoCompra solicitacao){
        return atualizar(cotacaoId,solicitacao,AcaoHistoricoPlano.AJUSTAR_PLANO,"Ajustou produtos e quantidades do plano");
    }

    @Transactional
    public VisaoComparacao atualizar(Long cotacaoId,SolicitacaoPlanoCompra solicitacao,AcaoHistoricoPlano acao,String descricao){
        historico.validarVersaoBase(cotacaoId,solicitacao.baseVersionId());
        historico.preparar(cotacaoId);
        aplicar(cotacaoId,solicitacao);
        estadoPedidos.invalidar(cotacaoId,usuarioAtual.companyId());
        return historico.registrar(cotacaoId,acao,descricao);
    }

    @Transactional
    public PreviaManualPedidoMinimo preverPedidoMinimo(Long cotacaoId,Long respostaId,SolicitacaoPlanoCompra solicitacao){
        historico.validarVersaoBase(cotacaoId,solicitacao.baseVersionId());
        VisaoComparacao base=comparacao.compare(cotacaoId,usuarioAtual.companyId());
        RespostaCotacao alvo=respostas.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(usuarioAtual.companyId(),cotacaoId).stream()
            .filter(r->r.getId().equals(respostaId)).findFirst().orElseThrow(()->new RecursoNaoEncontradoException("Resposta não encontrada."));
        if(alvo.getValorMinimoPedido()==null)throw new RegraNegocioException("Esta distribuidora não informou valor mínimo de pedido.");
        aplicar(cotacaoId,solicitacao);
        VisaoComparacao projetada=comparacao.compare(cotacaoId,usuarioAtual.companyId());
        CompraSugerida grupo=projetada.suggestedPurchase().stream().filter(g->Objects.equals(g.responseId(),respostaId)).findFirst().orElse(null);
        BigDecimal total=grupo==null?BigDecimal.ZERO:grupo.total();
        BigDecimal falta=alvo.getValorMinimoPedido().subtract(total).max(BigDecimal.ZERO);
        StatusPedidoMinimo status=falta.signum()==0?StatusPedidoMinimo.ATENDIDO:StatusPedidoMinimo.ABAIXO_DO_MINIMO;
        Map<Long,Integer> desejadasBase=base.products().stream().collect(Collectors.toMap(ComparacaoProduto::quotationItemId,ComparacaoProduto::desiredQuantity));
        int extras=projetada.products().stream().mapToInt(p->Math.max(0,p.desiredQuantity()-desejadasBase.getOrDefault(p.quotationItemId(),p.requestedQuantity()))).sum();
        int faltantesBase=base.products().stream().mapToInt(ComparacaoProduto::missingQuantity).sum();
        int faltantesProjetados=projetada.products().stream().mapToInt(ComparacaoProduto::missingQuantity).sum();
        long baseId=historico.versaoAtualId(cotacaoId);
        TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        return new PreviaManualPedidoMinimo(projetada,respostaId,total,alvo.getValorMinimoPedido(),falta,status,
            projetada.bestCompositionTotal().subtract(base.bestCompositionTotal()),extras,Math.max(0,faltantesProjetados-faltantesBase),baseId);
    }

    private void aplicar(Long cotacaoId,SolicitacaoPlanoCompra solicitacao){
        Long empresaId=usuarioAtual.companyId(); Cotacao cotacao=cotacoes.findByEmpresaIdAndId(empresaId,cotacaoId)
            .orElseThrow(()->new RecursoNaoEncontradoException("Cotação não encontrada."));
        if(cotacao.getStatus()!=StatusCotacao.CLOSED)throw new RegraNegocioException("As quantidades finais só podem ser alteradas com a cotação fechada.");
        Map<Long,ItemCotacao> itens=cotacao.getItens().stream().filter(ItemCotacao::isAtivo)
            .collect(Collectors.toMap(ItemCotacao::getId,Function.identity()));
        if(solicitacao.items().size()!=itens.size()||solicitacao.items().stream().map(ItemPlanoCompra::quotationItemId).distinct().count()!=itens.size())
            throw new RegraNegocioException("Envie todos os itens da cotação uma única vez.");
        Map<Long,RespostaCotacao> porResposta=respostas.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(empresaId,cotacaoId).stream()
            .filter(r->r.isAtivo()&&r.getStatus()==StatusResposta.SUBMITTED&&r.isIncluidaCompraSugerida())
            .collect(Collectors.toMap(RespostaCotacao::getId,Function.identity()));
        Map<String,String> erros=new LinkedHashMap<>();
        for(ItemPlanoCompra entrada:solicitacao.items()){
            ItemCotacao item=itens.get(entrada.quotationItemId()); String prefixo="itens."+entrada.quotationItemId()+".";
            if(item==null){erros.put(prefixo+"quotationItemId","Item não pertence a esta cotação.");continue;}
            ItemRespostaCotacao oferta=localizarOferta(porResposta,entrada.selectedResponseId(),item.getId());
            if(entrada.selectedResponseId()!=null&&oferta==null)erros.put(prefixo+"selectedResponseId","Selecione uma oferta válida e incluída nesta compra.");
            if(entrada.championQuantity()!=null){
                if(oferta==null)erros.put(prefixo+"championQuantity","Selecione a distribuidora campeã.");
                if(entrada.championQuantity()>entrada.desiredQuantity())erros.put(prefixo+"championQuantity","A quantidade no campeão não pode superar a quantidade final desejada.");
                if(oferta!=null&&entrada.championQuantity()>oferta.getQuantidadeDisponivel()&&limpar(entrada.stockOverrideNote())==null)
                    erros.put(prefixo+"stockOverrideNote","Justifique o estoque adicional confirmado com o representante.");
            }
        }
        if(!erros.isEmpty())throw new ErroValidacaoNegocioException("Corrija as quantidades destacadas para continuar.",erros);
        for(ItemPlanoCompra entrada:solicitacao.items()){
            ItemCotacao item=itens.get(entrada.quotationItemId()); ItemRespostaCotacao oferta=localizarOferta(porResposta,entrada.selectedResponseId(),item.getId());
            if(!entrada.manualSelection()&&entrada.championQuantity()==null)oferta=null;
            EscolhaCompraCotacao escolha=escolhas.findByItemCotacaoId(item.getId()).orElseGet(EscolhaCompraCotacao::new);
            escolha.setItemCotacao(item); escolha.setItemRespostaCotacao(oferta); escolha.setEscolhidoPor(usuarioAtual.get());
            escolha.setCampeaoManual(entrada.manualSelection()||entrada.championQuantity()!=null); escolha.setQuantidadeDesejada(entrada.desiredQuantity());
            escolha.setQuantidadeCampeao(entrada.championQuantity());
            escolha.setJustificativaEstoque(oferta!=null&&entrada.championQuantity()!=null&&entrada.championQuantity()>oferta.getQuantidadeDisponivel()?limpar(entrada.stockOverrideNote()):null);
            escolhas.save(escolha);
        }
        escolhas.flush();
    }

    private ItemRespostaCotacao localizarOferta(Map<Long,RespostaCotacao> respostas,Long respostaId,Long itemId){
        if(respostaId==null)return null; RespostaCotacao r=respostas.get(respostaId); if(r==null)return null;
        return r.getItens().stream().filter(i->i.getItemCotacao().getId().equals(itemId)&&i.isDisponivel()&&i.getPrecoUnitario()!=null
            &&i.getPrecoUnitario().signum()>0&&i.getQuantidadeDisponivel()!=null&&i.getQuantidadeDisponivel()>0).findFirst().orElse(null);
    }
    private String limpar(String valor){return valor==null||valor.isBlank()?null:valor.trim();}
}
