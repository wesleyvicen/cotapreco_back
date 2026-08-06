package br.com.cotapreco.dto;

import br.com.cotapreco.enums.StatusResposta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CotacaoPublicaDtos {
    private CotacaoPublicaDtos() {}
    public record ItemCotacaoPublica(String gtin, String productName, Integer requestedQuantity) {}
    public record VisaoCotacaoPublica(String companyName, String quotationName, Instant expiresAt, int productCount,
        boolean acceptingResponses, List<ItemCotacaoPublica> items) {}
    public record SolicitacaoInicioResposta(@NotBlank @Size(max = 120) String representativeName,
        @NotBlank @Size(max = 160) String supplierName, @NotBlank @Size(max = 30) String phone,
        @Email @Size(max = 180) String email, @Size(max = 20) String supplierDocument) {}
    public record InicioResposta(String responseToken) {}
    public record VisaoItemResposta(Long id, String gtin, String productName, Integer requestedQuantity,
        BigDecimal unitPrice, Integer availableQuantity, boolean available, String observation) {}
    public record VisaoRespostaPublica(String responseToken, String companyName, String quotationName,
        String representativeName, String supplierName, StatusResposta status, Instant expiresAt,
        List<VisaoItemResposta> items) {}
    public record AtualizacaoItemResposta(@NotNull Long id, @DecimalMin(value = "0.0001", message = "Preço deve ser maior que zero") BigDecimal unitPrice,
        @Min(0) Integer availableQuantity, boolean available, @Size(max = 500) String observation) {}
    public record SolicitacaoAtualizacaoItens(@NotEmpty List<@Valid AtualizacaoItemResposta> items) {}
}
