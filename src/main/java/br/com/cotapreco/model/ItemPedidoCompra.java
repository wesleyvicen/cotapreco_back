package br.com.cotapreco.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity @Table(name="itens_pedido_compra") @Getter @Setter @NoArgsConstructor
public class ItemPedidoCompra {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="pedido_compra_id") private PedidoCompra pedidoCompra;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="item_cotacao_id") private ItemCotacao itemCotacao;
    @Column(length=14) private String ean;
    @Column(nullable=false,length=240) private String produto;
    @Column(nullable=false) private Integer quantidade;
    @Column(name="preco_unitario",nullable=false,precision=15,scale=4) private BigDecimal precoUnitario;
    @Column(nullable=false,precision=15,scale=4) private BigDecimal subtotal;
    @Column(name="justificativa_estoque",length=500) private String justificativaEstoque;
}
