package br.com.cotapreco.model;

import br.com.cotapreco.enums.StatusResposta;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Entity @Table(name = "respostas_cotacao") @Getter @Setter @NoArgsConstructor
public class RespostaCotacao {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "cotacao_id") private Cotacao cotacao;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "representante_id") private Representante representante;
    @Column(name = "token_resposta", nullable = false, unique = true, length = 80) private String tokenResposta;
    @Column(name = "nome_representante", nullable = false, length = 120) private String nomeRepresentante;
    @Column(name = "nome_distribuidora", nullable = false, length = 160) private String nomeDistribuidora;
    @Column(name = "telefone", nullable = false, length = 30) private String telefone;
    @Column(length = 180) private String email;
    @Column(name = "ativo", nullable = false) private boolean ativo = true;
    @Column(name = "documento_distribuidora", length = 20) private String documentoDistribuidora;
    @Column(name = "valor_minimo_pedido", precision = 15, scale = 2) private BigDecimal valorMinimoPedido;
    @Column(name = "incluida_compra_sugerida", nullable = false) private boolean incluidaCompraSugerida = true;
    @Column(name = "chave_distribuidora", nullable = false, length = 220) private String chaveDistribuidora;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private StatusResposta status = StatusResposta.IN_PROGRESS;
    @Column(name = "enviado_em") private Instant enviadoEm;
    @Column(name = "criado_em", nullable = false, updatable = false) private Instant criadoEm = Instant.now();
    @Column(name = "atualizado_em", nullable = false) private Instant atualizadoEm = Instant.now();
    @OneToMany(mappedBy = "respostaCotacao", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id") private List<ItemRespostaCotacao> itens = new ArrayList<>();
    @PreUpdate void atualizarData() { atualizadoEm = Instant.now(); }
}
