package br.com.cotapreco.repository;
import br.com.cotapreco.enums.StatusResposta;
import br.com.cotapreco.model.RespostaCotacao;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.*;
public interface RespostaCotacaoRepository extends JpaRepository<RespostaCotacao, Long> {
    @EntityGraph(attributePaths = {"itens", "itens.itemCotacao", "itens.itemCotacao.produto"})
    List<RespostaCotacao> findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(Long companyId, Long quotationId);
    @EntityGraph(attributePaths = {"cotacao", "cotacao.empresa", "itens", "itens.itemCotacao", "itens.itemCotacao.produto"})
    Optional<RespostaCotacao> findByTokenResposta(String responseToken);
    long countByCotacaoEmpresaIdAndStatusAndEnviadoEmBetween(Long companyId, StatusResposta status, Instant start, Instant end);
    long countByCotacaoIdAndStatus(Long quotationId, StatusResposta status);
}
