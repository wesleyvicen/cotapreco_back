package br.com.cotapreco.dto;

import br.com.cotapreco.enums.StatusResposta;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class CotacaoPublicaDtos {
    private CotacaoPublicaDtos() {}

    public record ItemCotacaoPublica(String ean, String nomeProduto, Integer quantidadeSolicitada) {}
    public record VisaoCotacaoPublica(String nomeEmpresa, String nomeCotacao, Instant expiraEm, int totalProdutos,
        boolean aceitaRespostas, List<ItemCotacaoPublica> itens) {}

    public record SolicitacaoNovaResposta(
        @NotBlank @Size(max = 160) String nomeDistribuidora,
        @Size(max = 20) String documentoDistribuidora) {}

    public record ResumoRespostaPublica(Long id, String nomeDistribuidora, String documentoDistribuidora,
        StatusResposta status, Instant enviadoEm, Instant atualizadoEm, int totalItensCotados, BigDecimal valorTotal) {}

    public record VisaoItemResposta(Long id, String ean, String nomeProduto, Integer quantidadeSolicitada,
        BigDecimal precoUnitario, Integer quantidadeDisponivel, boolean disponivel, String observacao) {}

    public record VisaoRespostaPublica(Long id, String nomeEmpresa, String nomeCotacao, String nomeRepresentante,
        String nomeDistribuidora, String documentoDistribuidora, StatusResposta status, Instant expiraEm,
        boolean podeCorrigir, List<VisaoItemResposta> itens) {}

    public record AtualizacaoItemResposta(@NotNull Long id,
        @DecimalMin(value = "0.0001", message = "Preço deve ser maior que zero") BigDecimal precoUnitario,
        @Min(0) Integer quantidadeDisponivel, boolean disponivel, @Size(max = 500) String observacao) {}

    public record SolicitacaoAtualizacaoResposta(
        @NotBlank @Size(max = 160) String nomeDistribuidora,
        @Size(max = 20) String documentoDistribuidora,
        @NotEmpty List<@Valid AtualizacaoItemResposta> itens,
        boolean autoSave) {}
}
