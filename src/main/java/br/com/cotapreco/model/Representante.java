package br.com.cotapreco.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "representantes")
@Getter @Setter @NoArgsConstructor
public class Representante {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 120) private String nome;
    @Column(nullable = false, unique = true, length = 11) private String telefone;
    @Column(nullable = false, unique = true, length = 180) private String email;
    @Column(name = "senha_hash", nullable = false, length = 100) private String senhaHash;
    @Column(nullable = false) private boolean ativo = true;
    @Column(name = "versao_autenticacao", nullable = false) private int versaoAutenticacao = 1;
    @Column(name = "ultimo_login_em") private Instant ultimoLoginEm;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm = Instant.now();
    @Column(name = "atualizado_em", nullable = false) private Instant atualizadoEm = Instant.now();
    @PreUpdate void atualizarData() { atualizadoEm = Instant.now(); }
}
