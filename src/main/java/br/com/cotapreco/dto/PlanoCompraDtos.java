package br.com.cotapreco.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
public final class PlanoCompraDtos {
    private PlanoCompraDtos(){}
    public record SolicitacaoPlanoCompra(@NotEmpty List<@Valid ItemPlanoCompra> items){}
    public record ItemPlanoCompra(@NotNull Long quotationItemId,@NotNull @Min(0) Integer desiredQuantity,
        Long selectedResponseId,@Min(1) Integer championQuantity,@Size(max=500) String stockOverrideNote,
        boolean manualSelection){}
}
