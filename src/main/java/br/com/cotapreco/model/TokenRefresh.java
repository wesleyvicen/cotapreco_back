package br.com.cotapreco.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "tokens_refresh")
@Getter @Setter @NoArgsConstructor
public class TokenRefresh {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "usuario_id") private Usuario usuario;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "representante_id") private Representante representante;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(nullable = false, length = 36) private String familia;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm;
    @Column(name = "expira_em", nullable = false) private Instant expiraEm;
    @Column(name = "revogado_em") private Instant revogadoEm;

    public boolean ativo() { return revogadoEm == null && expiraEm.isAfter(Instant.now()); }
}
