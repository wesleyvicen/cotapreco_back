package br.com.cotapreco.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name = "itens_resposta_cotacao", uniqueConstraints = @UniqueConstraint(columnNames = {"resposta_cotacao_id", "item_cotacao_id"}))
@Getter @Setter @NoArgsConstructor
public class ItemRespostaCotacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "resposta_cotacao_id") private RespostaCotacao respostaCotacao;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "item_cotacao_id") private ItemCotacao itemCotacao;
    @Column(name = "preco_unitario", precision = 15, scale = 4) private BigDecimal precoUnitario;
    @Column(name = "quantidade_disponivel") private Integer quantidadeDisponivel;
    @Column(name = "disponivel", nullable = false) private boolean disponivel;
    @Column(name = "observacao", length = 500) private String observacao;
}
