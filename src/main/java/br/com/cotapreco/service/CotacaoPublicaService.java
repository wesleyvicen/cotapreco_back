package br.com.cotapreco.service;

import br.com.cotapreco.dto.CotacaoPublicaDtos.*;
import br.com.cotapreco.enums.StatusResposta;
import br.com.cotapreco.exception.*;
import br.com.cotapreco.helper.GeradorToken;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class CotacaoPublicaService {
    private final CotacaoRepository quotationRepository; private final RespostaCotacaoRepository responseRepository; private final GeradorToken tokenGenerator;

    @Transactional(readOnly = true)
    public VisaoCotacaoPublica getCotacao(String token) {
        Cotacao q = quotationRepository.findByTokenPublico(token).orElseThrow(() -> new RecursoNaoEncontradoException("Cotação não encontrada."));
        return new VisaoCotacaoPublica(q.getEmpresa().getNome(), q.getNome(), q.getExpiraEm(), q.getItens().size(), q.podeReceberRespostas(), q.getItens().stream().map(i -> new ItemCotacaoPublica(i.getProduto().getGtin(), i.getProduto().getNome(), i.getQuantidadeSolicitada())).toList());
    }
    @Transactional
    public InicioResposta start(String quotationToken, SolicitacaoInicioResposta request) {
        Cotacao q = quotationRepository.findByTokenPublico(quotationToken).orElseThrow(() -> new RecursoNaoEncontradoException("Cotação não encontrada.")); ensureOpen(q);
        RespostaCotacao response = new RespostaCotacao(); response.setCotacao(q); response.setTokenResposta(tokenGenerator.generate()); response.setNomeRepresentante(request.representativeName().trim()); response.setNomeDistribuidora(request.supplierName().trim()); response.setTelefone(request.phone().trim()); response.setEmail(clean(request.email())); response.setDocumentoDistribuidora(clean(request.supplierDocument()));
        for (ItemCotacao qi : q.getItens()) { ItemRespostaCotacao item = new ItemRespostaCotacao(); item.setRespostaCotacao(response); item.setItemCotacao(qi); response.getItens().add(item); }
        responseRepository.save(response); return new InicioResposta(response.getTokenResposta());
    }
    @Transactional(readOnly = true) public VisaoRespostaPublica getResponse(String token) { return view(findResponse(token)); }
    @Transactional
    public VisaoRespostaPublica updateItems(String token, SolicitacaoAtualizacaoItens request) {
        RespostaCotacao response = findResponse(token); ensureEditable(response); Map<Long, ItemRespostaCotacao> owned = response.getItens().stream().collect(Collectors.toMap(ItemRespostaCotacao::getId, Function.identity()));
        if (request.items().size() != owned.size()) throw new RegraNegocioException("Envie todos os itens da resposta.");
        Set<Long> seen = new HashSet<>();
        for (AtualizacaoItemResposta input : request.items()) {
            ItemRespostaCotacao item = owned.get(input.id()); if (item == null || !seen.add(input.id())) throw new RegraNegocioException("Item inválido para esta resposta.");
            if (input.available()) { if (input.unitPrice() == null || input.availableQuantity() == null || input.availableQuantity() <= 0) throw new RegraNegocioException("Informe preço e quantidade disponível para os produtos marcados como disponíveis."); item.setDisponivel(true); item.setPrecoUnitario(input.unitPrice()); item.setQuantidadeDisponivel(input.availableQuantity()); }
            else { item.setDisponivel(false); item.setPrecoUnitario(null); item.setQuantidadeDisponivel(null); }
            item.setObservacao(clean(input.observation()));
        }
        return view(response);
    }
    @Transactional
    public VisaoRespostaPublica submit(String token) {
        RespostaCotacao response = findResponse(token); ensureEditable(response);
        if (response.getItens().stream().noneMatch(i -> i.isDisponivel() && i.getPrecoUnitario() != null && i.getQuantidadeDisponivel() != null && i.getQuantidadeDisponivel() > 0)) throw new RegraNegocioException("Informe ao menos um produto disponível antes de enviar.");
        response.setStatus(StatusResposta.SUBMITTED); response.setEnviadoEm(Instant.now()); return view(response);
    }
    private RespostaCotacao findResponse(String token) { return responseRepository.findByTokenResposta(token).orElseThrow(() -> new RecursoNaoEncontradoException("Resposta não encontrada.")); }
    private void ensureEditable(RespostaCotacao r) { if (r.getStatus() == StatusResposta.SUBMITTED) throw new RegraNegocioException("Esta cotação já foi enviada."); ensureOpen(r.getCotacao()); }
    private void ensureOpen(Cotacao q) { if (!q.podeReceberRespostas()) { if (q.getExpiraEm() != null && !q.getExpiraEm().isAfter(Instant.now())) throw new RegraNegocioException("O prazo desta cotação expirou."); throw new RegraNegocioException("Esta cotação não está aberta para respostas."); } }
    private VisaoRespostaPublica view(RespostaCotacao r) { return new VisaoRespostaPublica(r.getTokenResposta(), r.getCotacao().getEmpresa().getNome(), r.getCotacao().getNome(), r.getNomeRepresentante(), r.getNomeDistribuidora(), r.getStatus(), r.getCotacao().getExpiraEm(), r.getItens().stream().map(i -> new VisaoItemResposta(i.getId(), i.getItemCotacao().getProduto().getGtin(), i.getItemCotacao().getProduto().getNome(), i.getItemCotacao().getQuantidadeSolicitada(), i.getPrecoUnitario(), i.getQuantidadeDisponivel(), i.isDisponivel(), i.getObservacao())).toList()); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
