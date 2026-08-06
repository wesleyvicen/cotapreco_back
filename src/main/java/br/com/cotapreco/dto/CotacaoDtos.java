package br.com.cotapreco.dto;

import br.com.cotapreco.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CotacaoDtos {
    private CotacaoDtos() {}
    public record LinhaImportacao(int row, String gtin, String productName, Integer quantity, boolean valid,
        boolean productExists, Long productId, List<String> errors) {}
    public record PreviaImportacao(int totalRows, int validRows, int invalidRows, List<LinhaImportacao> lines) {}
    public record SolicitacaoItemCotacao(@NotBlank @Pattern(regexp = "\\d{8,14}") String gtin,
        @NotBlank @Size(max = 240) String productName, @NotNull @Min(1) Integer quantity) {}
    public record SolicitacaoCriacaoCotacao(@NotBlank @Size(max = 180) String name, Instant expiresAt,
        @NotEmpty List<@Valid SolicitacaoItemCotacao> items) {}
    public record VisaoItemCotacao(Long id, Long productId, String gtin, String productName, String laboratory, Integer requestedQuantity) {}
    public record ResumoCotacao(Long id, String name, StatusCotacao status, Instant expiresAt, Instant createdAt,
        int productCount, long submittedResponses) {}
    public record VisaoCotacao(Long id, String name, StatusCotacao status, Instant expiresAt, Instant createdAt,
        Instant updatedAt, String publicToken, String publicUrl, List<VisaoItemCotacao> items) {}
    public record VisaoResposta(Long id, String supplierName, String representativeName, String phone, String email,
        StatusResposta status, Instant submittedAt, Instant createdAt, long quotedItems, BigDecimal total) {}
    public record VisaoPainel(long openQuotations, long finishedQuotations, long responsesThisMonth,
        BigDecimal quotedValue, BigDecimal estimatedSavings, List<ResumoCotacao> latestQuotations) {}
}
