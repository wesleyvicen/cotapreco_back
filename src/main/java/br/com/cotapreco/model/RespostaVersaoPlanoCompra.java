package br.com.cotapreco.model;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="respostas_versao_plano_compra", uniqueConstraints=@UniqueConstraint(columnNames={"versao_id","resposta_cotacao_id"}))
@Getter @Setter @NoArgsConstructor
public class RespostaVersaoPlanoCompra {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="versao_id") private VersaoPlanoCompra versao;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="resposta_cotacao_id") private RespostaCotacao respostaCotacao;
    @Column(nullable=false) private boolean incluida;
}
