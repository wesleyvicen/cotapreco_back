package br.com.cotapreco.service;

import br.com.cotapreco.dto.EscolhaCompraDtos.*;
import br.com.cotapreco.enums.*;
import br.com.cotapreco.exception.*;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import br.com.cotapreco.security.UsuarioAtualService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class EscolhaCompraCotacaoService {
    private final CotacaoRepository repositorioCotacoes;
    private final RespostaCotacaoRepository repositorioRespostas;
    private final EscolhaCompraCotacaoRepository repositorioEscolhas;
    private final UsuarioAtualService usuarioAtual;
    private final EstadoPedidoCompraService estadoPedidos;
    private final HistoricoPlanoCompraService historico;

    @Transactional
    public VisaoEscolhaCompra escolher(Long cotacaoId, Long itemCotacaoId, SolicitacaoEscolhaCompra solicitacao) {
        Cotacao cotacao = buscarCotacao(cotacaoId);
        validarStatus(cotacao);
        historico.preparar(cotacaoId);
        ItemCotacao itemCotacao = cotacao.getItens().stream().filter(i -> i.getId().equals(itemCotacaoId)).findFirst()
            .orElseThrow(() -> new RecursoNaoEncontradoException("Item da cotação não encontrado."));
        RespostaCotacao resposta = repositorioRespostas.findById(solicitacao.responseId())
            .orElseThrow(() -> new RecursoNaoEncontradoException("Resposta não encontrada."));
        if (!resposta.getCotacao().getId().equals(cotacaoId) || !resposta.isAtivo() || resposta.getStatus() != StatusResposta.SUBMITTED)
            throw new RegraNegocioException("Selecione uma resposta enviada desta cotação.");
        ItemRespostaCotacao itemResposta = resposta.getItens().stream()
            .filter(i -> i.getItemCotacao().getId().equals(itemCotacaoId)).findFirst()
            .orElseThrow(() -> new RegraNegocioException("A distribuidora não respondeu este produto."));
        if (!ofertaValida(itemResposta)) throw new RegraNegocioException("A distribuidora não possui uma oferta válida para este produto.");
        EscolhaCompraCotacao escolha = repositorioEscolhas.findByItemCotacaoId(itemCotacaoId).orElseGet(EscolhaCompraCotacao::new);
        escolha.setItemCotacao(itemCotacao);
        escolha.setItemRespostaCotacao(itemResposta);
        escolha.setEscolhidoPor(usuarioAtual.get());
        escolha.setCampeaoManual(true);
        escolha.setQuantidadeCampeao(null);
        escolha.setJustificativaEstoque(null);
        repositorioEscolhas.save(escolha);
        repositorioEscolhas.flush();
        estadoPedidos.invalidar(cotacaoId, usuarioAtual.companyId());
        historico.registrar(cotacaoId, AcaoHistoricoPlano.TROCAR_DISTRIBUIDORA,
            "Trocou a distribuidora de " + itemCotacao.getProduto().getNome());
        return new VisaoEscolhaCompra(itemCotacaoId, resposta.getId());
    }

    @Transactional
    public void voltarAoAutomatico(Long cotacaoId, Long itemCotacaoId) {
        Cotacao cotacao = buscarCotacao(cotacaoId);
        validarStatus(cotacao);
        historico.preparar(cotacaoId);
        if (cotacao.getItens().stream().noneMatch(i -> i.getId().equals(itemCotacaoId)))
            throw new RecursoNaoEncontradoException("Item da cotação não encontrado.");
        repositorioEscolhas.findByItemCotacaoId(itemCotacaoId).ifPresent(escolha -> {
            escolha.setItemRespostaCotacao(null); escolha.setCampeaoManual(false); escolha.setQuantidadeCampeao(null); escolha.setJustificativaEstoque(null);
            if (escolha.getQuantidadeDesejada() == null || escolha.getQuantidadeDesejada().equals(escolha.getItemCotacao().getQuantidadeSolicitada())) repositorioEscolhas.delete(escolha);
        });
        repositorioEscolhas.flush();
        estadoPedidos.invalidar(cotacaoId, usuarioAtual.companyId());
        ItemCotacao item=cotacao.getItens().stream().filter(i->i.getId().equals(itemCotacaoId)).findFirst().orElseThrow();
        historico.registrar(cotacaoId, AcaoHistoricoPlano.VOLTAR_AO_AUTOMATICO,
            "Restaurou o cálculo automático de " + item.getProduto().getNome());
    }

    private Cotacao buscarCotacao(Long id) {
        return repositorioCotacoes.findByEmpresaIdAndId(usuarioAtual.companyId(), id)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Cotação não encontrada."));
    }
    private void validarStatus(Cotacao cotacao) {
        if (cotacao.getStatus() != StatusCotacao.OPEN && cotacao.getStatus() != StatusCotacao.CLOSED)
            throw new RegraNegocioException("A compra sugerida só pode ser alterada em cotações abertas ou fechadas.");
    }
    private boolean ofertaValida(ItemRespostaCotacao item) {
        return item.isDisponivel() && item.getPrecoUnitario() != null && item.getPrecoUnitario().signum() > 0
            && item.getQuantidadeDisponivel() != null && item.getQuantidadeDisponivel() > 0;
    }
}
