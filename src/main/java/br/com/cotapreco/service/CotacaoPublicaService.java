package br.com.cotapreco.service;

import br.com.cotapreco.dto.CotacaoPublicaDtos.*;
import br.com.cotapreco.enums.StatusResposta;
import br.com.cotapreco.exception.*;
import br.com.cotapreco.helper.GeradorToken;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class CotacaoPublicaService {
    private final CotacaoRepository repositorioCotacoes;
    private final RespostaCotacaoRepository repositorioRespostas;
    private final GeradorToken geradorToken;

    @Transactional(readOnly = true)
    public VisaoCotacaoPublica obterCotacao(String token) {
        Cotacao cotacao = buscarCotacao(token);
        return new VisaoCotacaoPublica(cotacao.getEmpresa().getNome(), cotacao.getNome(), cotacao.getExpiraEm(),
            cotacao.getItens().size(), cotacao.podeReceberRespostas(), cotacao.getItens().stream()
                .map(item -> new ItemCotacaoPublica(item.getProduto().getEan(), item.getProduto().getNome(), item.getQuantidadeSolicitada())).toList());
    }

    @Transactional(readOnly = true)
    public List<ResumoRespostaPublica> listarMinhasRespostas(String tokenCotacao, Representante representante) {
        Cotacao cotacao = buscarCotacao(tokenCotacao);
        return repositorioRespostas.findAllByCotacaoIdAndRepresentanteIdOrderByCriadoEmDesc(cotacao.getId(), representante.getId())
            .stream().map(this::resumir).toList();
    }

    @Transactional
    public VisaoRespostaPublica criarResposta(String tokenCotacao, SolicitacaoNovaResposta solicitacao, Representante representante) {
        Cotacao cotacao = buscarCotacao(tokenCotacao);
        garantirAberta(cotacao);
        String documento = normalizarDocumento(solicitacao.documentoDistribuidora());
        String chave = chaveDistribuidora(solicitacao.nomeDistribuidora(), documento);
        if (repositorioRespostas.existsByCotacaoIdAndRepresentanteIdAndChaveDistribuidora(cotacao.getId(), representante.getId(), chave))
            throw new RegraNegocioException("Você já possui uma proposta para esta distribuidora. Abra a proposta existente para corrigi-la.");

        RespostaCotacao resposta = new RespostaCotacao();
        resposta.setCotacao(cotacao);
        resposta.setRepresentante(representante);
        resposta.setTokenResposta(geradorToken.generate());
        resposta.setNomeRepresentante(representante.getNome());
        resposta.setTelefone(representante.getTelefone());
        resposta.setEmail(representante.getEmail());
        preencherDistribuidora(resposta, solicitacao.nomeDistribuidora(), documento, chave);
        for (ItemCotacao itemCotacao : cotacao.getItens()) {
            ItemRespostaCotacao item = new ItemRespostaCotacao();
            item.setRespostaCotacao(resposta);
            item.setItemCotacao(itemCotacao);
            resposta.getItens().add(item);
        }
        try { repositorioRespostas.saveAndFlush(resposta); }
        catch (DataIntegrityViolationException ex) { throw new RegraNegocioException("Você já possui uma proposta para esta distribuidora."); }
        return visualizar(resposta);
    }

    @Transactional(readOnly = true)
    public VisaoRespostaPublica obterResposta(String tokenCotacao, Long respostaId, Representante representante) {
        return visualizar(buscarResposta(tokenCotacao, respostaId, representante));
    }

    @Transactional
    public VisaoRespostaPublica atualizarResposta(String tokenCotacao, Long respostaId, SolicitacaoAtualizacaoResposta solicitacao, Representante representante) {
        RespostaCotacao resposta = buscarResposta(tokenCotacao, respostaId, representante);
        garantirAberta(resposta.getCotacao());
        String documento = normalizarDocumento(solicitacao.documentoDistribuidora());
        String chave = chaveDistribuidora(solicitacao.nomeDistribuidora(), documento);
        if (repositorioRespostas.existsByCotacaoIdAndRepresentanteIdAndChaveDistribuidoraAndIdNot(
            resposta.getCotacao().getId(), representante.getId(), chave, resposta.getId()))
            throw new RegraNegocioException("Você já possui outra proposta para esta distribuidora.");

        Map<Long, ItemRespostaCotacao> itensDaResposta = resposta.getItens().stream()
            .collect(Collectors.toMap(ItemRespostaCotacao::getId, Function.identity()));
        if (solicitacao.itens().size() != itensDaResposta.size()) throw new RegraNegocioException("Envie todos os itens da resposta.");
        Set<Long> recebidos = new HashSet<>();
        Map<String, String> erros = new LinkedHashMap<>();
        for (AtualizacaoItemResposta entrada : solicitacao.itens()) {
            ItemRespostaCotacao item = itensDaResposta.get(entrada.id());
            if (item == null || !recebidos.add(entrada.id())) throw new RegraNegocioException("Item inválido para esta resposta.");
            if (entrada.disponivel()) {
                String prefixo = "itens." + entrada.id() + ".";
                if (entrada.precoUnitario() == null || entrada.precoUnitario().signum() <= 0)
                    erros.put(prefixo + "precoUnitario", "Informe um preço unitário maior que zero.");
                if (entrada.quantidadeDisponivel() == null || entrada.quantidadeDisponivel() <= 0)
                    erros.put(prefixo + "quantidadeDisponivel", "Informe uma quantidade disponível maior que zero.");
                if (entrada.quantidadeDisponivel() != null && entrada.quantidadeDisponivel() > item.getItemCotacao().getQuantidadeSolicitada()
                    && limpar(entrada.observacao()) == null)
                    erros.put(prefixo + "observacao", "Explique por que a quantidade disponível é maior que a solicitada.");
            }
        }
        if (!erros.isEmpty()) throw new ErroValidacaoNegocioException("Corrija os produtos destacados para continuar.", erros);
        for (AtualizacaoItemResposta entrada : solicitacao.itens()) {
            ItemRespostaCotacao item = itensDaResposta.get(entrada.id());
            if (entrada.disponivel()) {
                item.setDisponivel(true);
                item.setPrecoUnitario(entrada.precoUnitario());
                item.setQuantidadeDisponivel(entrada.quantidadeDisponivel());
            } else {
                item.setDisponivel(false);
                item.setPrecoUnitario(null);
                item.setQuantidadeDisponivel(null);
            }
            item.setObservacao(limpar(entrada.observacao()));
        }
        preencherDistribuidora(resposta, solicitacao.nomeDistribuidora(), documento, chave);
        resposta.setAtualizadoEm(Instant.now());
        if (resposta.getStatus() == StatusResposta.SUBMITTED) resposta.setEnviadoEm(Instant.now());
        try { repositorioRespostas.saveAndFlush(resposta); }
        catch (DataIntegrityViolationException ex) { throw new RegraNegocioException("Você já possui outra proposta para esta distribuidora."); }
        return visualizar(resposta);
    }

    @Transactional
    public VisaoRespostaPublica enviarResposta(String tokenCotacao, Long respostaId, Representante representante) {
        RespostaCotacao resposta = buscarResposta(tokenCotacao, respostaId, representante);
        garantirAberta(resposta.getCotacao());
        validarItensPersistidos(resposta);
        if (resposta.getItens().stream().noneMatch(this::itemValido))
            throw new RegraNegocioException("Informe ao menos um produto disponível antes de enviar.");
        resposta.setStatus(StatusResposta.SUBMITTED);
        resposta.setEnviadoEm(Instant.now());
        return visualizar(resposta);
    }

    private Cotacao buscarCotacao(String token) {
        return repositorioCotacoes.findByTokenPublico(token).orElseThrow(() -> new RecursoNaoEncontradoException("Cotação não encontrada."));
    }
    private RespostaCotacao buscarResposta(String tokenCotacao, Long respostaId, Representante representante) {
        return repositorioRespostas.findByIdAndCotacaoTokenPublicoAndRepresentanteId(respostaId, tokenCotacao, representante.getId())
            .orElseThrow(() -> new RecursoNaoEncontradoException("Proposta não encontrada."));
    }
    private void garantirAberta(Cotacao cotacao) {
        if (!cotacao.podeReceberRespostas()) {
            if (cotacao.getExpiraEm() != null && !cotacao.getExpiraEm().isAfter(Instant.now()))
                throw new RegraNegocioException("O prazo desta cotação expirou.");
            throw new RegraNegocioException("Esta cotação não está aberta para respostas.");
        }
    }
    private void preencherDistribuidora(RespostaCotacao resposta, String nome, String documento, String chave) {
        resposta.setNomeDistribuidora(nome.trim());
        resposta.setDocumentoDistribuidora(documento);
        resposta.setChaveDistribuidora(chave);
    }
    private String normalizarDocumento(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String numeros = valor.replaceAll("\\D", "");
        if (numeros.length() != 14) throw new RegraNegocioException("Informe um CNPJ com 14 dígitos.");
        return numeros;
    }
    private String chaveDistribuidora(String nome, String documento) {
        if (documento != null) return "cnpj:" + documento;
        String normalizado = Normalizer.normalize(nome.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "").replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
        return "nome:" + normalizado;
    }
    private boolean itemValido(ItemRespostaCotacao item) {
        return item.isDisponivel() && item.getPrecoUnitario() != null && item.getPrecoUnitario().signum() > 0
            && item.getQuantidadeDisponivel() != null && item.getQuantidadeDisponivel() > 0;
    }
    private void validarItensPersistidos(RespostaCotacao resposta) {
        Map<String, String> erros = new LinkedHashMap<>();
        for (ItemRespostaCotacao item : resposta.getItens()) if (item.isDisponivel()) {
            String prefixo = "itens." + item.getId() + ".";
            if (item.getPrecoUnitario() == null || item.getPrecoUnitario().signum() <= 0)
                erros.put(prefixo + "precoUnitario", "Informe um preço unitário maior que zero.");
            if (item.getQuantidadeDisponivel() == null || item.getQuantidadeDisponivel() <= 0)
                erros.put(prefixo + "quantidadeDisponivel", "Informe uma quantidade disponível maior que zero.");
            if (item.getQuantidadeDisponivel() != null && item.getQuantidadeDisponivel() > item.getItemCotacao().getQuantidadeSolicitada()
                && limpar(item.getObservacao()) == null)
                erros.put(prefixo + "observacao", "Explique por que a quantidade disponível é maior que a solicitada.");
        }
        if (!erros.isEmpty()) throw new ErroValidacaoNegocioException("Corrija os produtos destacados para continuar.", erros);
    }
    private ResumoRespostaPublica resumir(RespostaCotacao resposta) {
        int totalItens = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (ItemRespostaCotacao item : resposta.getItens()) if (itemValido(item)) {
            totalItens++;
            int quantidade = Math.min(item.getQuantidadeDisponivel(), item.getItemCotacao().getQuantidadeSolicitada());
            total = total.add(item.getPrecoUnitario().multiply(BigDecimal.valueOf(quantidade)));
        }
        return new ResumoRespostaPublica(resposta.getId(), resposta.getNomeDistribuidora(), resposta.getDocumentoDistribuidora(),
            resposta.getStatus(), resposta.getEnviadoEm(), resposta.getAtualizadoEm(), totalItens, total);
    }
    private VisaoRespostaPublica visualizar(RespostaCotacao resposta) {
        return new VisaoRespostaPublica(resposta.getId(), resposta.getCotacao().getEmpresa().getNome(), resposta.getCotacao().getNome(),
            resposta.getNomeRepresentante(), resposta.getNomeDistribuidora(), resposta.getDocumentoDistribuidora(), resposta.getStatus(),
            resposta.getCotacao().getExpiraEm(), resposta.getCotacao().podeReceberRespostas(), resposta.getItens().stream()
                .map(item -> new VisaoItemResposta(item.getId(), item.getItemCotacao().getProduto().getEan(),
                    item.getItemCotacao().getProduto().getNome(), item.getItemCotacao().getQuantidadeSolicitada(), item.getPrecoUnitario(),
                    item.getQuantidadeDisponivel(), item.isDisponivel(), item.getObservacao())).toList());
    }
    private String limpar(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
}
