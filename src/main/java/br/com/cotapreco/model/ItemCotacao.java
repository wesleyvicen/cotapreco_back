package br.com.cotapreco.model;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "itens_cotacao", uniqueConstraints = @UniqueConstraint(columnNames = {"cotacao_id", "produto_id"}))
@Getter @Setter @NoArgsConstructor
public class ItemCotacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cotacao_id") private Cotacao cotacao;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "produto_id") private Produto produto;
    @Column(name = "quantidade_solicitada", nullable = false) private Integer quantidadeSolicitada;
    @Column(name = "laboratorio_solicitado", length = 160) private String laboratorioSolicitado;
    @Column(name = "ativo", nullable = false) private boolean ativo = true;
}
