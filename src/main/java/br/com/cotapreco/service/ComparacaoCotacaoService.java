package br.com.cotapreco.service;

import br.com.cotapreco.dto.ComparacaoDtos.*;
import br.com.cotapreco.enums.StatusResposta;
import br.com.cotapreco.exception.RecursoNaoEncontradoException;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service @RequiredArgsConstructor
public class ComparacaoCotacaoService {
    private final CotacaoRepository quotationRepository; private final RespostaCotacaoRepository responseRepository;

    @Transactional(readOnly = true)
    public VisaoComparacao compare(Long quotationId, Long companyId) {
        Cotacao quotation = quotationRepository.findByEmpresaIdAndId(companyId, quotationId).orElseThrow(() -> new RecursoNaoEncontradoException("Cotação não encontrada."));
        List<RespostaCotacao> responses = responseRepository.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(companyId, quotationId).stream().filter(r -> r.getStatus() == StatusResposta.SUBMITTED).toList();
        List<TotalDistribuidor> supplierTotals = responses.stream().map(this::supplierTotal).toList();
        Map<Long, Allocation> allocations = new LinkedHashMap<>(); List<ComparacaoProduto> products = new ArrayList<>();
        int withoutOffer = 0, partiallyCovered = 0;
        for (ItemCotacao item : quotation.getItens()) {
            List<OfferRef> refs = responses.stream().flatMap(r -> r.getItens().stream().filter(ri -> ri.getItemCotacao().getId().equals(item.getId()) && valid(ri)).map(ri -> new OfferRef(r, ri))).sorted(Comparator.comparing(o -> o.item.getPrecoUnitario())).toList();
            BigDecimal best = refs.isEmpty() ? null : refs.getFirst().item.getPrecoUnitario();
            List<OfertaDistribuidor> offers = refs.stream().map(o -> new OfertaDistribuidor(o.response.getId(), o.response.getNomeDistribuidora(), o.item.getPrecoUnitario(), o.item.getQuantidadeDisponivel(), o.item.getPrecoUnitario().multiply(BigDecimal.valueOf(Math.min(o.item.getQuantidadeDisponivel(), item.getQuantidadeSolicitada()))), o.item.getPrecoUnitario().compareTo(best) == 0)).toList();
            int remaining = item.getQuantidadeSolicitada(); Set<Long> suppliersForProduct = new HashSet<>();
            for (OfferRef offer : refs) { if (remaining == 0) break; int quantity = Math.min(remaining, offer.item.getQuantidadeDisponivel()); if (quantity <= 0) continue; Allocation a = allocations.computeIfAbsent(offer.response.getId(), k -> new Allocation(offer.response.getNomeDistribuidora())); a.totalQuantity += quantity; a.total = a.total.add(offer.item.getPrecoUnitario().multiply(BigDecimal.valueOf(quantity))); if (suppliersForProduct.add(offer.response.getId())) a.productCount++; remaining -= quantity; }
            if (refs.isEmpty()) withoutOffer++; else if (remaining > 0) partiallyCovered++;
            products.add(new ComparacaoProduto(item.getId(), item.getProduto().getGtin(), item.getProduto().getNome(), item.getQuantidadeSolicitada(), offers, refs.isEmpty() ? null : refs.getFirst().response.getNomeDistribuidora(), best, item.getQuantidadeSolicitada() - remaining, remaining));
        }
        List<CompraSugerida> suggested = allocations.values().stream().map(a -> new CompraSugerida(a.name, a.productCount, a.totalQuantity, a.total)).toList();
        BigDecimal composition = suggested.stream().map(CompraSugerida::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal highest = supplierTotals.stream().map(TotalDistribuidor::total).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal savings = highest.subtract(composition).max(BigDecimal.ZERO);
        return new VisaoComparacao(products, supplierTotals, suggested, withoutOffer, partiallyCovered, composition, savings);
    }
    private boolean valid(ItemRespostaCotacao i) { return i.isDisponivel() && i.getPrecoUnitario() != null && i.getPrecoUnitario().signum() > 0 && i.getQuantidadeDisponivel() != null && i.getQuantidadeDisponivel() > 0; }
    private TotalDistribuidor supplierTotal(RespostaCotacao r) { int count = 0; BigDecimal total = BigDecimal.ZERO; for (var i : r.getItens()) if (valid(i)) { count++; total = total.add(i.getPrecoUnitario().multiply(BigDecimal.valueOf(Math.min(i.getQuantidadeDisponivel(), i.getItemCotacao().getQuantidadeSolicitada())))); } return new TotalDistribuidor(r.getId(), r.getNomeDistribuidora(), count, total); }
    private record OfferRef(RespostaCotacao response, ItemRespostaCotacao item) {}
    private static class Allocation { final String name; int productCount; int totalQuantity; BigDecimal total = BigDecimal.ZERO; Allocation(String name) { this.name = name; } }
}
