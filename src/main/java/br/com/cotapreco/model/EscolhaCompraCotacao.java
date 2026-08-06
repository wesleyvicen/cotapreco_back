package br.com.cotapreco.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "escolhas_compra_cotacao")
@Getter @Setter @NoArgsConstructor
public class EscolhaCompraCotacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "item_cotacao_id", unique = true) private ItemCotacao itemCotacao;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "item_resposta_cotacao_id") private ItemRespostaCotacao itemRespostaCotacao;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "escolhido_por") private Usuario escolhidoPor;
    @Column(name = "campeao_manual", nullable = false) private boolean campeaoManual = true;
    @Column(name = "quantidade_desejada") private Integer quantidadeDesejada;
    @Column(name = "quantidade_campeao") private Integer quantidadeCampeao;
    @Column(name = "justificativa_estoque", length = 500) private String justificativaEstoque;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm = Instant.now();
    @Column(name = "atualizado_em", nullable = false) private Instant atualizadoEm = Instant.now();
    @PreUpdate void atualizarData() { atualizadoEm = Instant.now(); }
}
