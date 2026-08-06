package br.com.cotapreco.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class ProdutoDtos {
    private ProdutoDtos() {}
    public record SolicitacaoProduto(
        @JsonAlias("gtin") @Pattern(regexp = "^\\s*$|\\d{8,14}", message = "EAN deve conter de 8 a 14 dígitos") String ean,
        @NotBlank @Size(max = 240) String name, @Size(max = 160) String laboratory,
        @Size(max = 160) String presentation, @Size(max = 120) String category, Boolean active) {}
    public record VisaoProduto(Long id, String ean, String name, String laboratory, String presentation,
        String category, boolean active, Instant createdAt, Instant updatedAt) {}
}
