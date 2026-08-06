package br.com.cotapreco.model;

import br.com.cotapreco.enums.StatusPedidoCompra;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity @Table(name="pedidos_compra", uniqueConstraints=@UniqueConstraint(columnNames={"cotacao_id","resposta_cotacao_id"}))
@Getter @Setter @NoArgsConstructor
public class PedidoCompra {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="cotacao_id") private Cotacao cotacao;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="resposta_cotacao_id") private RespostaCotacao respostaCotacao;
    @Column(nullable=false, unique=true, length=40) private String numero;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=30) private StatusPedidoCompra status;
    @Column(name="nome_farmacia",nullable=false,length=160) private String nomeFarmacia;
    @Column(name="cnpj_farmacia",nullable=false,length=14) private String cnpjFarmacia;
    @Column(name="nome_distribuidora",nullable=false,length=160) private String nomeDistribuidora;
    @Column(name="cnpj_distribuidora",length=14) private String cnpjDistribuidora;
    @Column(name="nome_representante",nullable=false,length=120) private String nomeRepresentante;
    @Column(name="telefone_representante",nullable=false,length=30) private String telefoneRepresentante;
    @Column(name="email_representante",length=180) private String emailRepresentante;
    @Column(length=500) private String observacao;
    @Column(nullable=false,precision=15,scale=4) private BigDecimal total;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="criado_por") private Usuario criadoPor;
    @Column(name="gerado_em",nullable=false) private Instant geradoEm;
    @Column(name="compartilhado_em") private Instant compartilhadoEm;
    @Column(name="atualizado_em",nullable=false) private Instant atualizadoEm;
    @OneToMany(mappedBy="pedidoCompra",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("id") private List<ItemPedidoCompra> itens=new ArrayList<>();
    @PreUpdate void atualizarData(){atualizadoEm=Instant.now();}
}
