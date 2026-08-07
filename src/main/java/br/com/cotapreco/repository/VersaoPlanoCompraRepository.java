package br.com.cotapreco.repository;

import br.com.cotapreco.model.VersaoPlanoCompra;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface VersaoPlanoCompraRepository extends JpaRepository<VersaoPlanoCompra,Long> {
    @EntityGraph(attributePaths={"criadoPor","restauradaDe"})
    List<VersaoPlanoCompra> findAllByCotacaoIdOrderByNumeroDesc(Long cotacaoId);
    Optional<VersaoPlanoCompra> findTopByCotacaoIdOrderByNumeroDesc(Long cotacaoId);
    Optional<VersaoPlanoCompra> findByIdAndCotacaoId(Long id,Long cotacaoId);
}
