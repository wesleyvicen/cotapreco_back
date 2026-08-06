package br.com.cotapreco.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "tokens_redefinicao_senha_representante")
@Getter @Setter @NoArgsConstructor
public class TokenRedefinicaoSenhaRepresentante {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "representante_id") private Representante representante;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expira_em", nullable = false) private Instant expiraEm;
    @Column(name = "utilizado_em") private Instant utilizadoEm;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm = Instant.now();

    public boolean valido() { return utilizadoEm == null && expiraEm.isAfter(Instant.now()); }
}
