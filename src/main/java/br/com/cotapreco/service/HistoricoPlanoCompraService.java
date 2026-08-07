package br.com.cotapreco.service;

import br.com.cotapreco.dto.ComparacaoDtos.VisaoComparacao;
import br.com.cotapreco.dto.HistoricoPlanoDtos.*;
import br.com.cotapreco.enums.*;
import br.com.cotapreco.exception.*;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import br.com.cotapreco.security.UsuarioAtualService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class HistoricoPlanoCompraService {
    private final CotacaoRepository cotacoes;
    private final RespostaCotacaoRepository respostas;
    private final EscolhaCompraCotacaoRepository escolhas;
    private final VersaoPlanoCompraRepository versoes;
    private final ItemVersaoPlanoCompraRepository itensVersao;
    private final RespostaVersaoPlanoCompraRepository respostasVersao;
    private final ComparacaoCotacaoService comparacao;
    private final EstadoPedidoCompraService estadoPedidos;
    private final UsuarioAtualService usuarioAtual;

    @Transactional(readOnly=true)
    public HistoricoPlano historico(Long cotacaoId) {
        Cotacao cotacao=buscar(cotacaoId);
        List<VersaoPlanoCompra> lista=versoes.findAllByCotacaoIdOrderByNumeroDesc(cotacao.getId());
        Long atual=lista.isEmpty()?null:lista.getFirst().getId();
        return new HistoricoPlano(atual==null?0:atual,lista.size()>1,lista.stream().map(v->visualizar(v,Objects.equals(v.getId(),atual))).toList());
    }

    @Transactional
    public void preparar(Long cotacaoId) {
        Cotacao cotacao=buscar(cotacaoId);
        if(versoes.findTopByCotacaoIdOrderByNumeroDesc(cotacaoId).isEmpty())
            capturar(cotacao,AcaoHistoricoPlano.ESTADO_INICIAL,"Estado inicial do plano",comparacao.compare(cotacaoId,usuarioAtual.companyId()).bestCompositionTotal(),null);
    }

    @Transactional
    public VisaoComparacao registrar(Long cotacaoId,AcaoHistoricoPlano acao,String descricao) {
        Cotacao cotacao=buscar(cotacaoId);
        VisaoComparacao atual=comparacao.compare(cotacaoId,usuarioAtual.companyId());
        capturar(cotacao,acao,descricao,atual.bestCompositionTotal(),null);
        return atual;
    }

    @Transactional(readOnly=true)
    public long versaoAtualId(Long cotacaoId) {
        buscar(cotacaoId);
        return versoes.findTopByCotacaoIdOrderByNumeroDesc(cotacaoId).map(VersaoPlanoCompra::getId).orElse(0L);
    }

    @Transactional(readOnly=true)
    public void validarVersaoBase(Long cotacaoId,Long baseVersionId) {
        if(baseVersionId==null)return;
        long atual=versoes.findTopByCotacaoIdOrderByNumeroDesc(cotacaoId).map(VersaoPlanoCompra::getId).orElse(0L);
        if(baseVersionId!=atual)throw new ConflitoEstadoException("O plano foi alterado em outra aba. Atualize a página antes de salvar seus ajustes.");
    }

    @Transactional
    public ResultadoRestauracao desfazer(Long cotacaoId) {
        List<VersaoPlanoCompra> lista=versoes.findAllByCotacaoIdOrderByNumeroDesc(buscar(cotacaoId).getId());
        if(lista.size()<2)throw new RegraNegocioException("Ainda não existe uma alteração do plano para desfazer.");
        return restaurarInterno(cotacaoId,lista.get(1),"Última alteração desfeita.");
    }

    @Transactional
    public ResultadoRestauracao restaurar(Long cotacaoId,Long versaoId) {
        Cotacao cotacao=buscar(cotacaoId);
        VersaoPlanoCompra versao=versoes.findByIdAndCotacaoId(versaoId,cotacao.getId())
            .orElseThrow(()->new RecursoNaoEncontradoException("Versão do plano não encontrada."));
        return restaurarInterno(cotacaoId,versao,"Versão do plano restaurada.");
    }

    private ResultadoRestauracao restaurarInterno(Long cotacaoId,VersaoPlanoCompra versao,String mensagem) {
        Cotacao cotacao=buscar(cotacaoId);
        if(cotacao.getStatus()!=StatusCotacao.CLOSED)throw new RegraNegocioException("O histórico só pode ser restaurado com a cotação fechada.");
        String bloqueio=motivoBloqueio(cotacao,versao);
        if(bloqueio!=null)throw new RegraNegocioException(bloqueio);
        preparar(cotacaoId);
        List<ItemVersaoPlanoCompra> itens=itensVersao.findAllByVersaoId(versao.getId());
        List<EscolhaCompraCotacao> atuais=escolhas.findAllByItemCotacaoCotacaoId(cotacaoId);
        escolhas.deleteAll(atuais); escolhas.flush();
        Usuario usuario=usuarioAtual.get();
        for(ItemVersaoPlanoCompra snapshot:itens)if(snapshot.isEscolhaPresente()){
            EscolhaCompraCotacao escolha=new EscolhaCompraCotacao();
            escolha.setItemCotacao(snapshot.getItemCotacao()); escolha.setItemRespostaCotacao(snapshot.getItemRespostaCotacao());
            escolha.setEscolhidoPor(usuario); escolha.setCampeaoManual(snapshot.isCampeaoManual());
            escolha.setQuantidadeDesejada(snapshot.getQuantidadeDesejada()); escolha.setQuantidadeCampeao(snapshot.getQuantidadeCampeao());
            escolha.setJustificativaEstoque(snapshot.getJustificativaEstoque()); escolhas.save(escolha);
        }
        Map<Long,RespostaCotacao> atuaisResposta=respostas.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(usuarioAtual.companyId(),cotacaoId)
            .stream().collect(Collectors.toMap(RespostaCotacao::getId,Function.identity()));
        for(RespostaVersaoPlanoCompra snapshot:respostasVersao.findAllByVersaoId(versao.getId())){
            RespostaCotacao resposta=atuaisResposta.get(snapshot.getRespostaCotacao().getId());
            if(resposta!=null)resposta.setIncluidaCompraSugerida(snapshot.isIncluida());
        }
        respostas.saveAll(atuaisResposta.values()); respostas.flush(); escolhas.flush();
        estadoPedidos.invalidar(cotacaoId,usuarioAtual.companyId());
        VisaoComparacao atual=comparacao.compare(cotacaoId,usuarioAtual.companyId());
        capturar(cotacao,AcaoHistoricoPlano.RESTAURAR_VERSAO,
            "Restaurou a versão "+versao.getNumero()+" — "+versao.getDescricao(),atual.bestCompositionTotal(),versao);
        return new ResultadoRestauracao(mensagem,atual,historico(cotacaoId));
    }

    private VersaoPlanoCompra capturar(Cotacao cotacao,AcaoHistoricoPlano acao,String descricao,BigDecimal total,VersaoPlanoCompra restauradaDe) {
        int numero=versoes.findTopByCotacaoIdOrderByNumeroDesc(cotacao.getId()).map(v->v.getNumero()+1).orElse(1);
        VersaoPlanoCompra versao=new VersaoPlanoCompra(); versao.setCotacao(cotacao); versao.setNumero(numero); versao.setAcao(acao);
        versao.setDescricao(descricao); versao.setCriadoPor(usuarioAtual.get()); versao.setRestauradaDe(restauradaDe);
        versao.setTotalPlano(total); versao=versoes.saveAndFlush(versao);
        Map<Long,EscolhaCompraCotacao> porItem=escolhas.findAllByItemCotacaoCotacaoId(cotacao.getId()).stream()
            .collect(Collectors.toMap(e->e.getItemCotacao().getId(),Function.identity()));
        for(ItemCotacao item:cotacao.getItens().stream().filter(ItemCotacao::isAtivo).toList()){
            EscolhaCompraCotacao escolha=porItem.get(item.getId()); ItemVersaoPlanoCompra snapshot=new ItemVersaoPlanoCompra();
            snapshot.setVersao(versao); snapshot.setItemCotacao(item); snapshot.setEscolhaPresente(escolha!=null);
            snapshot.setItemRespostaCotacao(escolha==null?null:escolha.getItemRespostaCotacao());
            snapshot.setCampeaoManual(escolha!=null&&escolha.isCampeaoManual());
            snapshot.setQuantidadeDesejada(escolha==null?null:escolha.getQuantidadeDesejada());
            snapshot.setQuantidadeCampeao(escolha==null?null:escolha.getQuantidadeCampeao());
            snapshot.setJustificativaEstoque(escolha==null?null:escolha.getJustificativaEstoque()); itensVersao.save(snapshot);
        }
        for(RespostaCotacao resposta:respostas.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(usuarioAtual.companyId(),cotacao.getId())){
            RespostaVersaoPlanoCompra snapshot=new RespostaVersaoPlanoCompra(); snapshot.setVersao(versao);
            snapshot.setRespostaCotacao(resposta); snapshot.setIncluida(resposta.isIncluidaCompraSugerida()); respostasVersao.save(snapshot);
        }
        itensVersao.flush(); respostasVersao.flush(); return versao;
    }

    private VersaoPlano visualizar(VersaoPlanoCompra versao,boolean atual) {
        String bloqueio=motivoBloqueio(versao.getCotacao(),versao);
        return new VersaoPlano(versao.getId(),versao.getNumero(),versao.getAcao(),versao.getDescricao(),versao.getCriadoPor().getNome(),
            versao.getCriadoEm(),versao.getTotalPlano(),atual,bloqueio==null,bloqueio);
    }

    private String motivoBloqueio(Cotacao cotacao,VersaoPlanoCompra versao) {
        List<ItemVersaoPlanoCompra> itens=itensVersao.findAllByVersaoId(versao.getId());
        Set<Long> ativos=cotacao.getItens().stream().filter(ItemCotacao::isAtivo).map(ItemCotacao::getId).collect(Collectors.toSet());
        Set<Long> snapshot=itens.stream().map(i->i.getItemCotacao().getId()).collect(Collectors.toSet());
        if(!ativos.equals(snapshot))return "Os produtos ativos da cotação mudaram desde esta versão.";
        Set<Long> respostasAtuais=respostas.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(usuarioAtual.companyId(),cotacao.getId())
            .stream().map(RespostaCotacao::getId).collect(Collectors.toSet());
        Set<Long> respostasSnapshot=respostasVersao.findAllByVersaoId(versao.getId()).stream()
            .map(r->r.getRespostaCotacao().getId()).collect(Collectors.toSet());
        if(!respostasAtuais.equals(respostasSnapshot))return "As respostas da cotação mudaram desde esta versão.";
        for(ItemVersaoPlanoCompra item:itens)if(item.isEscolhaPresente()&&item.getItemRespostaCotacao()!=null){
            ItemRespostaCotacao oferta=item.getItemRespostaCotacao(); RespostaCotacao resposta=oferta.getRespostaCotacao();
            if(!resposta.isAtivo()||resposta.getStatus()!=StatusResposta.SUBMITTED||!oferta.isDisponivel()||oferta.getPrecoUnitario()==null
                ||oferta.getPrecoUnitario().signum()<=0||oferta.getQuantidadeDisponivel()==null||oferta.getQuantidadeDisponivel()<=0)
                return "Uma oferta usada nesta versão não está mais disponível.";
        }
        return null;
    }

    private Cotacao buscar(Long cotacaoId) {
        return cotacoes.findByEmpresaIdAndId(usuarioAtual.companyId(),cotacaoId)
            .orElseThrow(()->new RecursoNaoEncontradoException("Cotação não encontrada."));
    }
}
