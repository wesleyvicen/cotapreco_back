package br.com.cotapreco.model;

import br.com.cotapreco.enums.PerfilUsuario;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "usuarios", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
@Getter @Setter @NoArgsConstructor
public class Usuario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id") private Empresa empresa;
    @Column(name = "nome", nullable = false, length = 120) private String nome;
    @Column(nullable = false, length = 180) private String email;
    @Column(name = "senha_hash", nullable = false, length = 100) private String senhaHash;
    @Enumerated(EnumType.STRING) @Column(name = "perfil", nullable = false, length = 20) private PerfilUsuario perfil;
    @Column(name = "ativo", nullable = false) private boolean ativo = true;
    @Column(name = "versao_autenticacao", nullable = false) private int versaoAutenticacao = 1;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
