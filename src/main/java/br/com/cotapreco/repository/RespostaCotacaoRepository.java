package br.com.cotapreco.repository;

import br.com.cotapreco.enums.StatusResposta;
import br.com.cotapreco.model.RespostaCotacao;
import org.springframework.data.jpa.repository.*;
import java.time.Instant;
import java.util.*;

public interface RespostaCotacaoRepository extends JpaRepository<RespostaCotacao, Long> {
    @EntityGraph(attributePaths = {"itens", "itens.itemCotacao", "itens.itemCotacao.produto"})
    List<RespostaCotacao> findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(Long empresaId, Long cotacaoId);

    @EntityGraph(attributePaths = {"cotacao", "cotacao.empresa", "itens", "itens.itemCotacao", "itens.itemCotacao.produto"})
    List<RespostaCotacao> findAllByCotacaoIdAndRepresentanteIdOrderByCriadoEmDesc(Long cotacaoId, Long representanteId);

    @EntityGraph(attributePaths = {"cotacao", "cotacao.empresa", "itens", "itens.itemCotacao", "itens.itemCotacao.produto"})
    Optional<RespostaCotacao> findByIdAndCotacaoTokenPublicoAndRepresentanteId(Long id, String tokenPublico, Long representanteId);

    boolean existsByCotacaoIdAndRepresentanteIdAndChaveDistribuidora(Long cotacaoId, Long representanteId, String chaveDistribuidora);
    boolean existsByCotacaoIdAndRepresentanteIdAndChaveDistribuidoraAndIdNot(Long cotacaoId, Long representanteId, String chaveDistribuidora, Long id);
    long countByCotacaoEmpresaIdAndStatusAndEnviadoEmBetween(Long empresaId, StatusResposta status, Instant inicio, Instant fim);
    long countByCotacaoIdAndStatus(Long cotacaoId, StatusResposta status);
}
