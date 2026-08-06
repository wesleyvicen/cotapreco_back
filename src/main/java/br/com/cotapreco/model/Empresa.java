package br.com.cotapreco.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "empresas") @Getter @Setter @NoArgsConstructor
public class Empresa {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "nome", nullable = false, length = 160) private String nome;
    @Column(nullable = false, unique = true, length = 160) private String slug;
    @Column(name = "ativo", nullable = false) private boolean ativo = true;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm = Instant.now();
}
