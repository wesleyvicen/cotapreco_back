package br.com.cotapreco.repository;

import br.com.cotapreco.model.ItemVersaoPlanoCompra;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface ItemVersaoPlanoCompraRepository extends JpaRepository<ItemVersaoPlanoCompra,Long> {
    @EntityGraph(attributePaths={"itemCotacao","itemRespostaCotacao","itemRespostaCotacao.respostaCotacao"})
    List<ItemVersaoPlanoCompra> findAllByVersaoId(Long versaoId);
}
