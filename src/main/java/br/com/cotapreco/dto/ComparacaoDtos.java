package br.com.cotapreco.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ComparacaoDtos {
    private ComparacaoDtos() {}
    public record OfertaDistribuidor(Long responseId, String supplierName, BigDecimal unitPrice, Integer availableQuantity,
        BigDecimal offeredTotal, boolean bestPrice) {}
    public record ComparacaoProduto(Long quotationItemId, String gtin, String productName, int requestedQuantity,
        List<OfertaDistribuidor> offers, String winningSupplier, BigDecimal bestUnitPrice, int coveredQuantity, int missingQuantity) {}
    public record TotalDistribuidor(Long responseId, String supplierName, int quotedItems, BigDecimal total) {}
    public record CompraSugerida(String supplierName, int productCount, int totalQuantity, BigDecimal total) {}
    public record VisaoComparacao(List<ComparacaoProduto> products, List<TotalDistribuidor> supplierTotals,
        List<CompraSugerida> suggestedPurchase, int productsWithoutOffer, int partiallyCoveredProducts,
        BigDecimal bestCompositionTotal, BigDecimal estimatedSavings) {}
}
