package br.com.cotapreco.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "produtos")
@Getter @Setter @NoArgsConstructor
public class Produto {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id") private Empresa empresa;
    @Column(length = 14) private String ean;
    @Column(name = "identificador_catalogo", nullable = false, length = 260) private String identificadorCatalogo;
    @Column(name = "nome", nullable = false, length = 240) private String nome;
    @Column(name = "laboratorio", length = 160) private String laboratorio;
    @Column(name = "apresentacao", length = 160) private String apresentacao;
    @Column(name = "categoria", length = 120) private String categoria;
    @Column(name = "ativo", nullable = false) private boolean ativo = true;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm = Instant.now();
    @Column(name = "atualizado_em", nullable = false) private Instant atualizadoEm = Instant.now();
    @PreUpdate void atualizarData() { atualizadoEm = Instant.now(); }
}
