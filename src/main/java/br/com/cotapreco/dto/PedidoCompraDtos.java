package br.com.cotapreco.dto;
import br.com.cotapreco.enums.StatusPedidoCompra;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
public final class PedidoCompraDtos {
    private PedidoCompraDtos(){}
    public record SolicitacaoGeracaoPedido(@Size(max=500) String observation){}
    public record SolicitacaoFinalizacao(boolean confirmPartialCoverage){}
    public record ItemPedido(Long quotationItemId,String ean,String productName,int quantity,BigDecimal unitPrice,
        BigDecimal subtotal,String stockOverrideNote){}
    public record VisaoPedido(Long id,Long responseId,String number,StatusPedidoCompra status,String supplierName,
        String supplierDocument,BigDecimal total,Instant generatedAt,Instant sharedAt,boolean pdfAvailable,List<ItemPedido> items){}
}
