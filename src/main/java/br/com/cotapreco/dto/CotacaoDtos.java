package br.com.cotapreco.dto;

import br.com.cotapreco.enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAlias;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CotacaoDtos {
    private CotacaoDtos() {}
    public record LinhaImportacao(int row, String ean, String productName, Integer quantity, String laboratory, boolean valid,
        boolean productExists, Long productId, List<String> errors) {}
    public record PreviaImportacao(int totalRows, int validRows, int invalidRows, List<LinhaImportacao> lines) {}
    public record ColunaArquivo(int index, String name) {}
    public record MapeamentoColunas(@Min(0) Integer ean, @NotNull @Min(0) Integer productName,
        @NotNull @Min(0) Integer quantity, @Min(0) Integer laboratory) {}
    public record AnaliseArquivoImportacao(String sheetName, int totalRows, List<ColunaArquivo> columns,
        MapeamentoColunas suggestedMapping, List<List<String>> sampleRows) {}
    public record ItemPreviaManual(Integer row, String ean, String productName, String quantity, String laboratory) {}
    public record SolicitacaoPreviaManual(@NotEmpty List<ItemPreviaManual> items) {}
    public record SolicitacaoItemCotacao(@JsonAlias("gtin") @Pattern(regexp = "^\\s*$|\\d{8,14}", message = "EAN deve conter de 8 a 14 dígitos") String ean,
        @NotBlank @Size(max = 240) String productName, @NotNull @Min(1) Integer quantity,
        @Size(max = 160) String laboratory) {}
    public record SolicitacaoCriacaoCotacao(@NotBlank @Size(max = 180) String name, Instant expiresAt,
        @NotEmpty List<@Valid SolicitacaoItemCotacao> items) {}
    public record VisaoItemCotacao(Long id, Long productId, String ean, String productName, String laboratory, Integer requestedQuantity) {}
    public record ResumoCotacao(Long id, String name, StatusCotacao status, Instant expiresAt, Instant createdAt,
        int productCount, long submittedResponses) {}
    public record VisaoCotacao(Long id, String name, StatusCotacao status, Instant expiresAt, Instant createdAt,
        Instant updatedAt, String publicToken, String publicUrl, List<VisaoItemCotacao> items) {}
    public record VisaoResposta(Long id, String supplierName, String representativeName, String phone, String email,
        StatusResposta status, Instant submittedAt, Instant createdAt, long quotedItems, BigDecimal total) {}
    public record VisaoPainel(long openQuotations, long finishedQuotations, long responsesThisMonth,
        BigDecimal quotedValue, BigDecimal estimatedSavings, List<ResumoCotacao> latestQuotations) {}
}
