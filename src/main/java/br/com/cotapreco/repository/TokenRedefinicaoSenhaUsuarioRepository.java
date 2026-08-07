package br.com.cotapreco.repository;

import br.com.cotapreco.model.TokenRedefinicaoSenhaUsuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TokenRedefinicaoSenhaUsuarioRepository extends JpaRepository<TokenRedefinicaoSenhaUsuario, Long> {
    Optional<TokenRedefinicaoSenhaUsuario> findByTokenHash(String tokenHash);
    long deleteByUsuarioId(Long usuarioId);
}
