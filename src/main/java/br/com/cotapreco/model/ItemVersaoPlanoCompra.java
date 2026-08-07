package br.com.cotapreco.model;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="itens_versao_plano_compra", uniqueConstraints=@UniqueConstraint(columnNames={"versao_id","item_cotacao_id"}))
@Getter @Setter @NoArgsConstructor
public class ItemVersaoPlanoCompra {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="versao_id") private VersaoPlanoCompra versao;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="item_cotacao_id") private ItemCotacao itemCotacao;
    @Column(name="escolha_presente",nullable=false) private boolean escolhaPresente;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="item_resposta_cotacao_id") private ItemRespostaCotacao itemRespostaCotacao;
    @Column(name="campeao_manual",nullable=false) private boolean campeaoManual;
    @Column(name="quantidade_desejada") private Integer quantidadeDesejada;
    @Column(name="quantidade_campeao") private Integer quantidadeCampeao;
    @Column(name="justificativa_estoque",length=500) private String justificativaEstoque;
}
