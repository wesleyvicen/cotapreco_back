package br.com.cotapreco.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ComparativoCompraDtos {
    private ComparativoCompraDtos() {}

    public enum SituacaoPrecoCompra { MELHOR_PRECO, ACIMA_DO_MELHOR_PRECO, REFERENCIA_INCOMPLETA }

    public record PontoHistoricoCompra(Long quotationId, String quotationName, Instant purchasedAt,
        int quantity, BigDecimal actualUnitPrice, BigDecimal actualTotal, BigDecimal bestAvailableUnitPrice,
        BigDecimal bestAvailableTotal, String supplierName, SituacaoPrecoCompra priceSituation) {}

    public record ProdutoHistoricoCompra(String key, String ean, String productName, String laboratory,
        List<PontoHistoricoCompra> points, BigDecimal firstUnitPrice, BigDecimal lastUnitPrice,
        BigDecimal priceVariation, BigDecimal priceVariationPercent, BigDecimal financialDifference,
        SituacaoPrecoCompra latestPriceSituation) {}

    public record ResumoComparativoCompra(int commonProducts, int evaluatedPurchases, int bestPricePurchases,
        BigDecimal actualTotal, BigDecimal amountAboveBestScenario, BigDecimal averagePriceVariationPercent) {}

    public record VisaoComparativoCompra(List<ProdutoHistoricoCompra> products,
        ResumoComparativoCompra summary) {}
}
