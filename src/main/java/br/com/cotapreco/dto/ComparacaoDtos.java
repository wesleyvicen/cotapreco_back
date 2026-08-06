package br.com.cotapreco.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ComparacaoDtos {
    private ComparacaoDtos() {}
    public record OfertaDistribuidor(Long responseId, String supplierName, BigDecimal unitPrice, Integer availableQuantity,
        BigDecimal offeredTotal, boolean bestPrice, int position, boolean manuallySelected) {}
    public record ComparacaoProduto(Long quotationItemId, String ean, String productName, int requestedQuantity, int desiredQuantity,
        List<OfertaDistribuidor> offers, String winningSupplier, BigDecimal bestUnitPrice, int coveredQuantity,
        int missingQuantity, Long selectedResponseId, boolean manualSelection, boolean invalidManualSelection,
        Integer championQuantity, Integer championAvailableQuantity, String stockOverrideNote) {}
    public record TotalDistribuidor(Long responseId, String supplierName, int quotedItems, BigDecimal total) {}
    public record LinhaCompraSugerida(Long quotationItemId, String ean, String productName, int allocatedQuantity,
        BigDecimal unitPrice, BigDecimal subtotal, int offerPosition, boolean champion, boolean complement,
        boolean manualSelection, String stockOverrideNote) {}
    public record CompraSugerida(Long responseId, String supplierName, int productCount, int totalQuantity,
        BigDecimal total, List<LinhaCompraSugerida> items) {}
    public record VisaoComparacao(List<ComparacaoProduto> products, List<TotalDistribuidor> supplierTotals,
        List<CompraSugerida> suggestedPurchase, int productsWithoutOffer, int partiallyCoveredProducts,
        BigDecimal bestCompositionTotal, BigDecimal estimatedSavings) {}
}
