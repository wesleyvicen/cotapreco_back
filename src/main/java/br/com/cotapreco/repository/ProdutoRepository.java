package br.com.cotapreco.repository;
import br.com.cotapreco.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    List<Produto> findAllByEmpresaIdOrderByNome(Long companyId);
    Optional<Produto> findByEmpresaIdAndId(Long companyId, Long id);
    Optional<Produto> findByEmpresaIdAndEan(Long empresaId, String ean);
    List<Produto> findAllByEmpresaIdAndEanIn(Long empresaId, Collection<String> eans);
    Optional<Produto> findByEmpresaIdAndIdentificadorCatalogo(Long empresaId, String identificadorCatalogo);
}
