package br.com.cotapreco.repository;

import br.com.cotapreco.model.EscolhaCompraCotacao;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface EscolhaCompraCotacaoRepository extends JpaRepository<EscolhaCompraCotacao, Long> {
    @EntityGraph(attributePaths = {"itemCotacao", "itemRespostaCotacao", "itemRespostaCotacao.respostaCotacao"})
    List<EscolhaCompraCotacao> findAllByItemCotacaoCotacaoId(Long cotacaoId);
    Optional<EscolhaCompraCotacao> findByItemCotacaoId(Long itemCotacaoId);
}
