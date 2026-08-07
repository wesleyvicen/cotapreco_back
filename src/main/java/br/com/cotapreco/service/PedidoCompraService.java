package br.com.cotapreco.service;
import br.com.cotapreco.dto.ComparacaoDtos.*;
import br.com.cotapreco.dto.PedidoCompraDtos.*;
import br.com.cotapreco.enums.*;
import br.com.cotapreco.exception.*;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import br.com.cotapreco.security.UsuarioAtualService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class PedidoCompraService {
    private final PedidoCompraRepository pedidos;private final CotacaoRepository cotacoes;private final RespostaCotacaoRepository respostas;
    private final UsuarioAtualService usuarioAtual;private final ComparacaoCotacaoService comparacao;private final GeradorPdfPedidoService pdf;private final GeradorImagemPedidoService imagem;
    @Transactional(readOnly=true) public List<VisaoPedido> listar(Long cotacaoId){buscarCotacao(cotacaoId);return pedidos.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByNomeDistribuidora(usuarioAtual.companyId(),cotacaoId).stream().map(this::visualizar).toList();}
    @Transactional public VisaoPedido gerar(Long cotacaoId,Long respostaId,SolicitacaoGeracaoPedido solicitacao){
        Cotacao cotacao=buscarCotacao(cotacaoId);if(cotacao.getStatus()!=StatusCotacao.CLOSED)throw new RegraNegocioException("Feche a cotação antes de gerar pedidos.");
        if(cotacao.getEmpresa().getCnpj()==null)throw new RegraNegocioException("Cadastre o CNPJ da farmácia antes de gerar pedidos.");
        CompraSugerida grupo=comparacao.compare(cotacaoId,usuarioAtual.companyId()).suggestedPurchase().stream().filter(g->g.responseId().equals(respostaId)).findFirst()
            .orElseThrow(()->new RegraNegocioException("Esta distribuidora não possui itens na compra sugerida."));
        boolean abaixoMinimo=grupo.minimumOrderStatus()==StatusPedidoMinimo.ABAIXO_DO_MINIMO;
        if(abaixoMinimo&&!solicitacao.confirmBelowMinimum())throw new RegraNegocioException("O pedido está abaixo do valor mínimo informado pela distribuidora. Confirme que deseja gerar mesmo com risco de rejeição.");
        RespostaCotacao resposta=respostas.findById(respostaId).filter(r->r.getCotacao().getId().equals(cotacaoId)&&r.getCotacao().getEmpresa().getId().equals(usuarioAtual.companyId()))
            .orElseThrow(()->new RecursoNaoEncontradoException("Resposta não encontrada."));
        PedidoCompra pedido=pedidos.findByCotacaoIdAndRespostaCotacaoId(cotacaoId,respostaId).orElseGet(PedidoCompra::new);Instant agora=Instant.now();
        pedido.setCotacao(cotacao);pedido.setRespostaCotacao(resposta);pedido.setNumero(String.format("PED-C%06d-D%06d",cotacaoId,respostaId));pedido.setStatus(StatusPedidoCompra.GERADO);
        pedido.setNomeFarmacia(cotacao.getEmpresa().getNome());pedido.setCnpjFarmacia(cotacao.getEmpresa().getCnpj());pedido.setNomeDistribuidora(resposta.getNomeDistribuidora());pedido.setCnpjDistribuidora(resposta.getDocumentoDistribuidora());
        pedido.setNomeRepresentante(resposta.getNomeRepresentante());pedido.setTelefoneRepresentante(resposta.getTelefone());pedido.setEmailRepresentante(resposta.getEmail());pedido.setObservacao(limpar(solicitacao.observation()));
        pedido.setTotal(grupo.total());pedido.setValorMinimoPedido(grupo.minimumOrderValue());pedido.setAbaixoMinimoConfirmado(abaixoMinimo&&solicitacao.confirmBelowMinimum());pedido.setCriadoPor(usuarioAtual.get());pedido.setGeradoEm(agora);pedido.setCompartilhadoEm(null);pedido.setAtualizadoEm(agora);pedido.getItens().clear();
        Map<Long,ItemCotacao> itens=cotacao.getItens().stream().collect(Collectors.toMap(ItemCotacao::getId,Function.identity()));
        for(LinhaCompraSugerida linha:grupo.items()){ItemPedidoCompra item=new ItemPedidoCompra();item.setPedidoCompra(pedido);item.setItemCotacao(itens.get(linha.quotationItemId()));item.setEan(linha.ean());item.setProduto(linha.productName());item.setQuantidade(linha.allocatedQuantity());item.setPrecoUnitario(linha.unitPrice());item.setSubtotal(linha.subtotal());item.setJustificativaEstoque(linha.stockOverrideNote());pedido.getItens().add(item);}
        return visualizar(pedidos.saveAndFlush(pedido));
    }
    @Transactional(readOnly=true) public byte[] pdf(Long cotacaoId,Long pedidoId){PedidoCompra pedido=buscarPedidoAtual(cotacaoId,pedidoId);return pdf.gerar(pedido);}
    @Transactional(readOnly=true) public byte[] imagem(Long cotacaoId,Long pedidoId){PedidoCompra pedido=buscarPedidoAtual(cotacaoId,pedidoId);return imagem.gerar(pedido);}
    @Transactional public VisaoPedido compartilhar(Long cotacaoId,Long pedidoId){PedidoCompra pedido=buscarPedido(cotacaoId,pedidoId);if(pedido.getStatus()!=StatusPedidoCompra.GERADO&&pedido.getStatus()!=StatusPedidoCompra.COMPARTILHADO)throw new RegraNegocioException("Gere novamente o pedido antes de compartilhar.");pedido.setStatus(StatusPedidoCompra.COMPARTILHADO);pedido.setCompartilhadoEm(Instant.now());return visualizar(pedido);}
    @Transactional public void finalizar(Long cotacaoId,SolicitacaoFinalizacao solicitacao){Cotacao cotacao=buscarCotacao(cotacaoId);if(cotacao.getStatus()==StatusCotacao.COMPLETED)return;if(cotacao.getStatus()!=StatusCotacao.CLOSED)throw new RegraNegocioException("A cotação está "+nomeStatus(cotacao.getStatus())+". Atualize a página; somente uma cotação fechada pode ser finalizada.");VisaoComparacao visao=comparacao.compare(cotacaoId,usuarioAtual.companyId());if(visao.suggestedPurchase().isEmpty())throw new RegraNegocioException("A compra sugerida não possui pedidos.");
        Map<Long,PedidoCompra> atuais=pedidos.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByNomeDistribuidora(usuarioAtual.companyId(),cotacaoId).stream().collect(Collectors.toMap(p->p.getRespostaCotacao().getId(),Function.identity()));
        boolean falta=visao.suggestedPurchase().stream().anyMatch(g->{PedidoCompra p=atuais.get(g.responseId());return p==null||(p.getStatus()!=StatusPedidoCompra.GERADO&&p.getStatus()!=StatusPedidoCompra.COMPARTILHADO);});if(falta)throw new RegraNegocioException("Gere novamente todos os pedidos da composição antes de finalizar.");
        if((visao.productsWithoutOffer()>0||visao.partiallyCoveredProducts()>0)&&!solicitacao.confirmPartialCoverage())throw new RegraNegocioException("Confirme que deseja finalizar mesmo com produtos sem cobertura.");cotacao.setStatus(StatusCotacao.COMPLETED);}
    private Cotacao buscarCotacao(Long id){return cotacoes.findByEmpresaIdAndId(usuarioAtual.companyId(),id).orElseThrow(()->new RecursoNaoEncontradoException("Cotação não encontrada."));}
    private PedidoCompra buscarPedido(Long cotacaoId,Long id){return pedidos.findByCotacaoEmpresaIdAndCotacaoIdAndId(usuarioAtual.companyId(),cotacaoId,id).orElseThrow(()->new RecursoNaoEncontradoException("Pedido não encontrado."));}
    private PedidoCompra buscarPedidoAtual(Long cotacaoId,Long id){PedidoCompra pedido=buscarPedido(cotacaoId,id);if(pedido.getStatus()==StatusPedidoCompra.DESATUALIZADO||pedido.getStatus()==StatusPedidoCompra.CANCELADO)throw new RegraNegocioException("Gere novamente o pedido antes de baixar o arquivo.");return pedido;}
    private String nomeStatus(StatusCotacao status){return switch(status){case DRAFT->"em rascunho";case OPEN->"aberta";case CLOSED->"fechada";case COMPLETED->"finalizada";case CANCELLED->"cancelada";};}
    private VisaoPedido visualizar(PedidoCompra p){boolean abaixo=p.getValorMinimoPedido()!=null&&p.getTotal().compareTo(p.getValorMinimoPedido())<0;return new VisaoPedido(p.getId(),p.getRespostaCotacao().getId(),p.getNumero(),p.getStatus(),p.getNomeDistribuidora(),p.getCnpjDistribuidora(),p.getTotal(),p.getValorMinimoPedido(),abaixo,p.isAbaixoMinimoConfirmado(),p.getGeradoEm(),p.getCompartilhadoEm(),p.getStatus()==StatusPedidoCompra.GERADO||p.getStatus()==StatusPedidoCompra.COMPARTILHADO,p.getItens().stream().map(i->new ItemPedido(i.getItemCotacao().getId(),i.getEan(),i.getProduto(),i.getQuantidade(),i.getPrecoUnitario(),i.getSubtotal(),i.getJustificativaEstoque())).toList());}
    private String limpar(String v){return v==null||v.isBlank()?null:v.trim();}
}
