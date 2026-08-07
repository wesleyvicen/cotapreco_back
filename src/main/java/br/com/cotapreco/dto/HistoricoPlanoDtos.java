package br.com.cotapreco.dto;

import br.com.cotapreco.dto.ComparacaoDtos.VisaoComparacao;
import br.com.cotapreco.enums.AcaoHistoricoPlano;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class HistoricoPlanoDtos {
    private HistoricoPlanoDtos() {}
    public record VersaoPlano(Long id,int number,AcaoHistoricoPlano action,String description,String createdBy,
        Instant createdAt,BigDecimal total,boolean current,boolean restorable,String blockedReason) {}
    public record HistoricoPlano(long currentVersionId,boolean canUndo,List<VersaoPlano> versions) {}
    public record ResultadoRestauracao(String message,VisaoComparacao comparison,HistoricoPlano history) {}
}
