package br.com.cotapreco.service;
import br.com.cotapreco.dto.ComparacaoDtos.VisaoComparacao;
import br.com.cotapreco.dto.PlanoCompraDtos.*;
import br.com.cotapreco.enums.*;
import br.com.cotapreco.exception.*;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import br.com.cotapreco.security.UsuarioAtualService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class PlanoCompraService {
    private final CotacaoRepository cotacoes;private final RespostaCotacaoRepository respostas;
    private final EscolhaCompraCotacaoRepository escolhas;private final UsuarioAtualService usuarioAtual;
    private final ComparacaoCotacaoService comparacao;private final EstadoPedidoCompraService estadoPedidos;
    @Transactional public VisaoComparacao atualizar(Long cotacaoId,SolicitacaoPlanoCompra solicitacao){
        Long empresaId=usuarioAtual.companyId();Cotacao cotacao=cotacoes.findByEmpresaIdAndId(empresaId,cotacaoId)
            .orElseThrow(()->new RecursoNaoEncontradoException("Cotação não encontrada."));
        if(cotacao.getStatus()!=StatusCotacao.CLOSED)throw new RegraNegocioException("As quantidades finais só podem ser alteradas com a cotação fechada.");
        Map<Long,ItemCotacao> itens=cotacao.getItens().stream().collect(Collectors.toMap(ItemCotacao::getId,Function.identity()));
        if(solicitacao.items().size()!=itens.size()||solicitacao.items().stream().map(ItemPlanoCompra::quotationItemId).distinct().count()!=itens.size())
            throw new RegraNegocioException("Envie todos os itens da cotação uma única vez.");
        Map<Long,RespostaCotacao> porResposta=respostas.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(empresaId,cotacaoId).stream()
            .filter(r->r.getStatus()==StatusResposta.SUBMITTED).collect(Collectors.toMap(RespostaCotacao::getId,Function.identity()));
        Map<String,String> erros=new LinkedHashMap<>();
        for(ItemPlanoCompra entrada:solicitacao.items()){
            ItemCotacao item=itens.get(entrada.quotationItemId());String prefixo="itens."+entrada.quotationItemId()+".";
            if(item==null){erros.put(prefixo+"quotationItemId","Item não pertence a esta cotação.");continue;}
            ItemRespostaCotacao oferta=localizarOferta(porResposta,entrada.selectedResponseId(),item.getId());
            if(entrada.selectedResponseId()!=null&&oferta==null)erros.put(prefixo+"selectedResponseId","Selecione uma oferta válida desta cotação.");
            if(entrada.championQuantity()!=null){
                if(oferta==null)erros.put(prefixo+"championQuantity","Selecione a distribuidora campeã.");
                if(entrada.championQuantity()>entrada.desiredQuantity())erros.put(prefixo+"championQuantity","A quantidade no campeão não pode superar a quantidade final desejada.");
                if(oferta!=null&&entrada.championQuantity()>oferta.getQuantidadeDisponivel()&&limpar(entrada.stockOverrideNote())==null)
                    erros.put(prefixo+"stockOverrideNote","Justifique o estoque adicional confirmado com o representante.");
            }
        }
        if(!erros.isEmpty())throw new ErroValidacaoNegocioException("Corrija as quantidades destacadas para continuar.",erros);
        for(ItemPlanoCompra entrada:solicitacao.items()){
            ItemCotacao item=itens.get(entrada.quotationItemId());ItemRespostaCotacao oferta=localizarOferta(porResposta,entrada.selectedResponseId(),item.getId());
            if(!entrada.manualSelection()&&entrada.championQuantity()==null)oferta=null;
            EscolhaCompraCotacao escolha=escolhas.findByItemCotacaoId(item.getId()).orElseGet(EscolhaCompraCotacao::new);
            escolha.setItemCotacao(item);escolha.setItemRespostaCotacao(oferta);escolha.setEscolhidoPor(usuarioAtual.get());
            escolha.setCampeaoManual(entrada.manualSelection()||entrada.championQuantity()!=null);escolha.setQuantidadeDesejada(entrada.desiredQuantity());
            escolha.setQuantidadeCampeao(entrada.championQuantity());
            escolha.setJustificativaEstoque(oferta!=null&&entrada.championQuantity()!=null&&entrada.championQuantity()>oferta.getQuantidadeDisponivel()?limpar(entrada.stockOverrideNote()):null);
            escolhas.save(escolha);
        }
        escolhas.flush();estadoPedidos.invalidar(cotacaoId,empresaId);return comparacao.compare(cotacaoId,empresaId);
    }
    private ItemRespostaCotacao localizarOferta(Map<Long,RespostaCotacao> respostas,Long respostaId,Long itemId){if(respostaId==null)return null;RespostaCotacao r=respostas.get(respostaId);if(r==null)return null;return r.getItens().stream().filter(i->i.getItemCotacao().getId().equals(itemId)&&i.isDisponivel()&&i.getPrecoUnitario()!=null&&i.getPrecoUnitario().signum()>0&&i.getQuantidadeDisponivel()!=null&&i.getQuantidadeDisponivel()>0).findFirst().orElse(null);}
    private String limpar(String valor){return valor==null||valor.isBlank()?null:valor.trim();}
}
