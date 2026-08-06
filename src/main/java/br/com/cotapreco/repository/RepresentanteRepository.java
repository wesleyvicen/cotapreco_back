package br.com.cotapreco.repository;

import br.com.cotapreco.model.Representante;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RepresentanteRepository extends JpaRepository<Representante, Long> {
    Optional<Representante> findByTelefone(String telefone);
    Optional<Representante> findByEmailIgnoreCase(String email);
    boolean existsByTelefone(String telefone);
    boolean existsByEmailIgnoreCase(String email);
}
