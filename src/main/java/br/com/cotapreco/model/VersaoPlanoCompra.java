package br.com.cotapreco.model;

import br.com.cotapreco.enums.AcaoHistoricoPlano;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="versoes_plano_compra", uniqueConstraints=@UniqueConstraint(columnNames={"cotacao_id","numero"}))
@Getter @Setter @NoArgsConstructor
public class VersaoPlanoCompra {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="cotacao_id") private Cotacao cotacao;
    @Column(nullable=false) private int numero;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private AcaoHistoricoPlano acao;
    @Column(nullable=false,length=240) private String descricao;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="criado_por") private Usuario criadoPor;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="restaurada_de_id") private VersaoPlanoCompra restauradaDe;
    @Column(name="total_plano",nullable=false,precision=15,scale=4) private BigDecimal totalPlano;
    @Column(name="criado_em",nullable=false,updatable=false) private Instant criadoEm=Instant.now();
}
