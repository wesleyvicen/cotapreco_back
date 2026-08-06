package br.com.cotapreco.dto;

import jakarta.validation.constraints.NotNull;

public final class EscolhaCompraDtos {
    private EscolhaCompraDtos() {}
    public record SolicitacaoEscolhaCompra(@NotNull Long responseId) {}
    public record VisaoEscolhaCompra(Long quotationItemId, Long responseId) {}
}
