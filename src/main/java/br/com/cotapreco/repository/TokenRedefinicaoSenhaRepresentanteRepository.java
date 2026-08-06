package br.com.cotapreco.repository;

import br.com.cotapreco.model.TokenRedefinicaoSenhaRepresentante;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TokenRedefinicaoSenhaRepresentanteRepository extends JpaRepository<TokenRedefinicaoSenhaRepresentante, Long> {
    Optional<TokenRedefinicaoSenhaRepresentante> findByTokenHash(String tokenHash);
}
