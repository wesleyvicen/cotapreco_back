package br.com.cotapreco.service;
import br.com.cotapreco.enums.StatusPedidoCompra;
import br.com.cotapreco.repository.PedidoCompraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class EstadoPedidoCompraService {
    private final PedidoCompraRepository pedidos;
    private final ComparacaoCotacaoService comparacao;
    public void invalidar(Long cotacaoId,Long empresaId){
        var ativos=comparacao.compare(cotacaoId,empresaId).suggestedPurchase().stream().map(p->p.responseId()).collect(Collectors.toSet());
        pedidos.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByNomeDistribuidora(empresaId,cotacaoId).forEach(p->{
            p.setStatus(ativos.contains(p.getRespostaCotacao().getId())?StatusPedidoCompra.DESATUALIZADO:StatusPedidoCompra.CANCELADO);
        });
    }
}
