package br.com.cotapreco.service;

import br.com.cotapreco.dto.ComparativoCompraDtos.SituacaoPrecoCompra;
import br.com.cotapreco.enums.StatusPedidoCompra;
import br.com.cotapreco.enums.StatusResposta;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import br.com.cotapreco.security.UsuarioAtualService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(org.mockito.junit.jupiter.MockitoExtension.class)
class ComparativoCompraServiceTest {
    @Mock CotacaoRepository cotacoes;
    @Mock PedidoCompraRepository pedidos;
    @Mock RespostaCotacaoRepository respostas;
    @Mock UsuarioAtualService usuarioAtual;
    @InjectMocks ComparativoCompraService service;

    @Test
    void comparaPrecoEfetivoComMelhorComposicaoEHistorico() {
        Cotacao primeira=cotacao(1L,"Compra de janeiro"), segunda=cotacao(2L,"Compra de fevereiro");
        ItemCotacao itemPrimeira=item(11L,primeira), itemSegunda=item(21L,segunda);
        PedidoCompra pedidoPrimeira=pedido(primeira,itemPrimeira,"12.00",Instant.parse("2026-01-10T10:00:00Z"));
        PedidoCompra pedidoSegunda=pedido(segunda,itemSegunda,"10.00",Instant.parse("2026-02-10T10:00:00Z"));
        when(usuarioAtual.companyId()).thenReturn(1L);
        when(cotacoes.findAllByEmpresaIdOrderByCriadoEmDesc(1L)).thenReturn(List.of(segunda,primeira));
        when(pedidos.findAllByCotacaoEmpresaIdAndCotacaoIdInAndStatusInOrderByGeradoEmAsc(eq(1L),anyCollection(),anyCollection())).thenReturn(List.of(pedidoPrimeira,pedidoSegunda));
        when(respostas.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(1L,1L)).thenReturn(List.of(oferta(itemPrimeira,"10.00",100)));
        when(respostas.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(1L,2L)).thenReturn(List.of(oferta(itemSegunda,"10.00",100)));

        var resultado=service.comparar("1,2");

        assertThat(resultado.summary().commonProducts()).isEqualTo(1);
        assertThat(resultado.summary().bestPricePurchases()).isEqualTo(1);
        assertThat(resultado.summary().amountAboveBestScenario()).isEqualByComparingTo("200.00");
        var produto=resultado.products().getFirst();
        assertThat(produto.points()).hasSize(2);
        assertThat(produto.points().getFirst().priceSituation()).isEqualTo(SituacaoPrecoCompra.ACIMA_DO_MELHOR_PRECO);
        assertThat(produto.points().getLast().priceSituation()).isEqualTo(SituacaoPrecoCompra.MELHOR_PRECO);
        assertThat(produto.priceVariation()).isEqualByComparingTo("-2.00");
    }

    private Cotacao cotacao(Long id,String nome){Cotacao c=new Cotacao();c.setId(id);c.setNome(nome);c.setCriadoEm(Instant.parse("2026-01-01T00:00:00Z"));return c;}
    private ItemCotacao item(Long id,Cotacao cotacao){Produto produto=new Produto();produto.setEan("7890000000001");produto.setNome("Dipirona 500mg");produto.setLaboratorio("Medley");ItemCotacao item=new ItemCotacao();item.setId(id);item.setCotacao(cotacao);item.setProduto(produto);item.setQuantidadeSolicitada(100);return item;}
    private PedidoCompra pedido(Cotacao cotacao,ItemCotacao item,String preco,Instant data){PedidoCompra pedido=new PedidoCompra();pedido.setCotacao(cotacao);pedido.setStatus(StatusPedidoCompra.GERADO);pedido.setGeradoEm(data);pedido.setNomeDistribuidora("Distribuidora Teste");ItemPedidoCompra linha=new ItemPedidoCompra();linha.setPedidoCompra(pedido);linha.setItemCotacao(item);linha.setQuantidade(100);linha.setPrecoUnitario(new BigDecimal(preco));linha.setSubtotal(new BigDecimal(preco).multiply(BigDecimal.valueOf(100)));pedido.getItens().add(linha);return pedido;}
    private RespostaCotacao oferta(ItemCotacao item,String preco,int quantidade){RespostaCotacao resposta=new RespostaCotacao();resposta.setAtivo(true);resposta.setStatus(StatusResposta.SUBMITTED);ItemRespostaCotacao linha=new ItemRespostaCotacao();linha.setItemCotacao(item);linha.setDisponivel(true);linha.setPrecoUnitario(new BigDecimal(preco));linha.setQuantidadeDisponivel(quantidade);resposta.getItens().add(linha);return resposta;}
}
