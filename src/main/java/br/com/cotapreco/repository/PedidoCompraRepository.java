package br.com.cotapreco.repository;
import br.com.cotapreco.model.PedidoCompra;
import br.com.cotapreco.enums.StatusPedidoCompra;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PedidoCompraRepository extends JpaRepository<PedidoCompra,Long>{
    List<PedidoCompra> findAllByCotacaoEmpresaIdAndCotacaoIdOrderByNomeDistribuidora(Long empresaId,Long cotacaoId);
    Optional<PedidoCompra> findByCotacaoEmpresaIdAndCotacaoIdAndId(Long empresaId,Long cotacaoId,Long id);
    Optional<PedidoCompra> findByCotacaoIdAndRespostaCotacaoId(Long cotacaoId,Long respostaId);
    boolean existsByCotacaoId(Long cotacaoId);
    @EntityGraph(attributePaths={"cotacao","respostaCotacao","itens","itens.itemCotacao","itens.itemCotacao.produto"})
    List<PedidoCompra> findAllByCotacaoEmpresaIdAndCotacaoIdInAndStatusInOrderByGeradoEmAsc(Long empresaId,Collection<Long> cotacaoIds,Collection<StatusPedidoCompra> statuses);
    @EntityGraph(attributePaths={"cotacao","itens"})
    List<PedidoCompra> findAllByCotacaoEmpresaIdAndStatusIn(Long empresaId,Collection<StatusPedidoCompra> statuses);
}
