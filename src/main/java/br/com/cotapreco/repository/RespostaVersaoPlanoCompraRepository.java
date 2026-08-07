package br.com.cotapreco.repository;

import br.com.cotapreco.model.RespostaVersaoPlanoCompra;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface RespostaVersaoPlanoCompraRepository extends JpaRepository<RespostaVersaoPlanoCompra,Long> {
    @EntityGraph(attributePaths={"respostaCotacao"})
    List<RespostaVersaoPlanoCompra> findAllByVersaoId(Long versaoId);
}
