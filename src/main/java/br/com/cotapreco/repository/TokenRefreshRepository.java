package br.com.cotapreco.repository;

import br.com.cotapreco.model.TokenRefresh;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.Optional;

public interface TokenRefreshRepository extends JpaRepository<TokenRefresh, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TokenRefresh t left join fetch t.usuario u left join fetch u.empresa left join fetch t.representante where t.tokenHash = :hash")
    Optional<TokenRefresh> findByTokenHashForUpdate(@Param("hash") String hash);

    @Modifying
    @Query("update TokenRefresh t set t.revogadoEm = :agora where t.familia = :familia and t.revogadoEm is null")
    void revogarFamilia(@Param("familia") String familia, @Param("agora") Instant agora);

    @Modifying
    @Query("update TokenRefresh t set t.revogadoEm = :agora where t.usuario.id = :usuarioId and t.revogadoEm is null")
    void revogarPorUsuario(@Param("usuarioId") Long usuarioId, @Param("agora") Instant agora);

    @Modifying
    @Query("update TokenRefresh t set t.revogadoEm = :agora where t.representante.id = :representanteId and t.revogadoEm is null")
    void revogarPorRepresentante(@Param("representanteId") Long representanteId, @Param("agora") Instant agora);
}
