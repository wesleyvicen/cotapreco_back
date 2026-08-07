package br.com.cotapreco.dto;

import br.com.cotapreco.dto.ComparacaoDtos.VisaoComparacao;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

public final class PedidoMinimoDtos {
    private PedidoMinimoDtos() {}

    public enum EstrategiaPedidoMinimo { ATINGIR_MINIMO, REPASSAR_PEDIDO }
    public enum TipoAjustePedidoMinimo { REALOCACAO, UNIDADES_EXTRAS, REPASSE }

    public record AjustePedidoMinimo(Long quotationItemId, String productName, TipoAjustePedidoMinimo type,
        int currentQuantity, int projectedQuantity, int extraQuantity, BigDecimal unitPrice,
        String destinationSupplier) {}

    public record OpcaoPedidoMinimo(boolean feasible, BigDecimal projectedSupplierTotal,
        BigDecimal projectedPurchaseTotal, BigDecimal purchaseIncrease, int extraUnits, int uncoveredUnits,
        List<AjustePedidoMinimo> adjustments) {}

    public record VisaoOpcoesPedidoMinimo(Long responseId, String supplierName, BigDecimal currentTotal,
        BigDecimal minimumOrderValue, BigDecimal shortfall, OpcaoPedidoMinimo reachMinimum,
        OpcaoPedidoMinimo reallocateOrder) {}

    public record SolicitacaoAplicacaoPedidoMinimo(@NotNull EstrategiaPedidoMinimo strategy) {}
    public record SolicitacaoInclusaoCompra(boolean included) {}
    public record ResultadoAplicacaoPedidoMinimo(String message, VisaoComparacao comparison) {}
}
