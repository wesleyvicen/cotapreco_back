package br.com.cotapreco.repository;
import br.com.cotapreco.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @EntityGraph(attributePaths = "empresa")
    Optional<Usuario> findByEmailIgnoreCase(String email);
}
