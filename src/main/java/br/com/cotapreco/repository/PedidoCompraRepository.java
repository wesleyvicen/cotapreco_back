package br.com.cotapreco.repository;
import br.com.cotapreco.model.PedidoCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface PedidoCompraRepository extends JpaRepository<PedidoCompra,Long>{
    List<PedidoCompra> findAllByCotacaoEmpresaIdAndCotacaoIdOrderByNomeDistribuidora(Long empresaId,Long cotacaoId);
    Optional<PedidoCompra> findByCotacaoEmpresaIdAndCotacaoIdAndId(Long empresaId,Long cotacaoId,Long id);
    Optional<PedidoCompra> findByCotacaoIdAndRespostaCotacaoId(Long cotacaoId,Long respostaId);
    boolean existsByCotacaoId(Long cotacaoId);
}
