package br.com.cotapreco.model;

import br.com.cotapreco.enums.StatusCotacao;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.*;

@Entity @Table(name = "cotacoes") @Getter @Setter @NoArgsConstructor
public class Cotacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "empresa_id") private Empresa empresa;
    @Column(name = "nome", nullable = false, length = 180) private String nome;
    @Column(name = "token_publico", unique = true, length = 80) private String tokenPublico;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private StatusCotacao status = StatusCotacao.DRAFT;
    @Column(name = "expira_em") private Instant expiraEm;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "criado_por") private Usuario criadoPor;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm = Instant.now();
    @Column(name = "atualizado_em", nullable = false) private Instant atualizadoEm = Instant.now();
    @OneToMany(mappedBy = "cotacao", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id") private List<ItemCotacao> itens = new ArrayList<>();
    @PreUpdate void atualizarData() { atualizadoEm = Instant.now(); }
    public boolean podeReceberRespostas() { return status == StatusCotacao.OPEN && (expiraEm == null || expiraEm.isAfter(Instant.now())); }
}
