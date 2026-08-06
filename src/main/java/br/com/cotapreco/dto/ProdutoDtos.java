package br.com.cotapreco.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class ProdutoDtos {
    private ProdutoDtos() {}
    public record SolicitacaoProduto(@NotBlank @Pattern(regexp = "\\d{8,14}", message = "GTIN deve conter de 8 a 14 dígitos") String gtin,
        @NotBlank @Size(max = 240) String name, @Size(max = 160) String laboratory,
        @Size(max = 160) String presentation, @Size(max = 120) String category, Boolean active) {}
    public record VisaoProduto(Long id, String gtin, String name, String laboratory, String presentation,
        String category, boolean active, Instant createdAt, Instant updatedAt) {}
}
