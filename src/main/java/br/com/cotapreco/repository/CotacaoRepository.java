package br.com.cotapreco.repository;
import br.com.cotapreco.enums.StatusCotacao;
import br.com.cotapreco.model.Cotacao;
import org.springframework.data.jpa.repository.*;
import java.util.*;
public interface CotacaoRepository extends JpaRepository<Cotacao, Long> {
    @EntityGraph(attributePaths = {"itens", "itens.produto"})
    Optional<Cotacao> findByEmpresaIdAndId(Long companyId, Long id);
    List<Cotacao> findAllByEmpresaIdOrderByCriadoEmDesc(Long companyId);
    Optional<Cotacao> findByTokenPublico(String token);
    long countByEmpresaIdAndStatus(Long companyId, StatusCotacao status);
}
