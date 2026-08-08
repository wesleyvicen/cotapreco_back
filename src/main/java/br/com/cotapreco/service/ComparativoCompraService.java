package br.com.cotapreco.service;

import static br.com.cotapreco.dto.ComparativoCompraDtos.*;

import br.com.cotapreco.enums.StatusPedidoCompra;
import br.com.cotapreco.enums.StatusResposta;
import br.com.cotapreco.exception.RegraNegocioException;
import br.com.cotapreco.exception.RecursoNaoEncontradoException;
import br.com.cotapreco.helper.NormalizadorProduto;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.CotacaoRepository;
import br.com.cotapreco.repository.PedidoCompraRepository;
import br.com.cotapreco.repository.RespostaCotacaoRepository;
import br.com.cotapreco.security.UsuarioAtualService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class ComparativoCompraService {
    private static final List<StatusPedidoCompra> PEDIDOS_VALIDOS=List.of(StatusPedidoCompra.GERADO,StatusPedidoCompra.COMPARTILHADO);
    private final CotacaoRepository cotacoes;
    private final PedidoCompraRepository pedidos;
    private final RespostaCotacaoRepository respostas;
    private final UsuarioAtualService usuarioAtual;

    @Transactional(readOnly=true)
    public VisaoComparativoCompra comparar(String ids) {
        List<Long> selecionadas=parseIds(ids); Long empresaId=usuarioAtual.companyId();
        Map<Long,Cotacao> cotacoesPorId=cotacoes.findAllByEmpresaIdOrderByCriadoEmDesc(empresaId).stream().collect(Collectors.toMap(Cotacao::getId,c->c));
        if(selecionadas.stream().anyMatch(id->!cotacoesPorId.containsKey(id)))throw new RecursoNaoEncontradoException("Uma das cotações selecionadas não foi encontrada.");
        List<PedidoCompra> pedidosSelecionados=pedidos.findAllByCotacaoEmpresaIdAndCotacaoIdInAndStatusInOrderByGeradoEmAsc(empresaId,selecionadas,PEDIDOS_VALIDOS);
        Map<Long,List<PedidoCompra>> pedidosPorCotacao=pedidosSelecionados.stream().collect(Collectors.groupingBy(p->p.getCotacao().getId()));
        if(selecionadas.stream().anyMatch(id->pedidosPorCotacao.getOrDefault(id,List.of()).isEmpty()))throw new RegraNegocioException("Selecione somente cotações com pedidos gerados ou compartilhados.");
        Map<Long,List<RespostaCotacao>> respostasPorCotacao=new HashMap<>();
        for(Long id:selecionadas)respostasPorCotacao.put(id,respostas.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(empresaId,id));

        Map<String,ProdutoAcumulado> produtos=new LinkedHashMap<>();
        for(Long cotacaoId:selecionadas){
            Cotacao cotacao=cotacoesPorId.get(cotacaoId);
            Map<Long,List<ItemPedidoCompra>> itensPorItemCotacao=pedidosPorCotacao.get(cotacaoId).stream().flatMap(p->p.getItens().stream()).collect(Collectors.groupingBy(i->i.getItemCotacao().getId()));
            for(List<ItemPedidoCompra> itens:itensPorItemCotacao.values()){
                ItemCotacao itemCotacao=itens.getFirst().getItemCotacao(); String chave=chave(itemCotacao);
                int quantidade=itens.stream().mapToInt(ItemPedidoCompra::getQuantidade).sum();
                BigDecimal totalPago=itens.stream().map(ItemPedidoCompra::getSubtotal).reduce(BigDecimal.ZERO,BigDecimal::add);
                BigDecimal precoPago=dividir(totalPago,quantidade); Instant data=pedidosPorCotacao.get(cotacaoId).stream().filter(p->p.getItens().stream().anyMatch(i->i.getItemCotacao().getId().equals(itemCotacao.getId()))).map(PedidoCompra::getGeradoEm).max(Instant::compareTo).orElse(cotacao.getCriadoEm());
                String fornecedores=itens.stream().map(i->i.getPedidoCompra().getNomeDistribuidora()).distinct().collect(Collectors.joining(", "));
                Referencia referencia=melhorReferencia(itemCotacao,quantidade,respostasPorCotacao.get(cotacaoId));
                SituacaoPrecoCompra situacao=referencia.completa?(totalPago.compareTo(referencia.total)<=0?SituacaoPrecoCompra.MELHOR_PRECO:SituacaoPrecoCompra.ACIMA_DO_MELHOR_PRECO):SituacaoPrecoCompra.REFERENCIA_INCOMPLETA;
                Produto produto=itemCotacao.getProduto(); String laboratorio=laboratorio(itemCotacao);
                produtos.computeIfAbsent(chave,k->new ProdutoAcumulado(chave,produto.getEan(),produto.getNome(),laboratorio)).pontos.add(new PontoHistoricoCompra(cotacaoId,cotacao.getNome(),data,quantidade,precoPago,totalPago,referencia.preco,referencia.total,fornecedores,situacao));
            }
        }
        List<ProdutoHistoricoCompra> resultado=produtos.values().stream().filter(p->p.pontos.size()>=2).map(this::visualizar).sorted(Comparator.comparing(ProdutoHistoricoCompra::financialDifference).reversed()).toList();
        List<PontoHistoricoCompra> pontos=resultado.stream().flatMap(p->p.points().stream()).toList();
        int avaliadas=(int)pontos.stream().filter(p->p.priceSituation()!=SituacaoPrecoCompra.REFERENCIA_INCOMPLETA).count();
        int melhores=(int)pontos.stream().filter(p->p.priceSituation()==SituacaoPrecoCompra.MELHOR_PRECO).count();
        BigDecimal total=pontos.stream().map(PontoHistoricoCompra::actualTotal).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal acima=pontos.stream().filter(p->p.bestAvailableTotal()!=null).map(p->p.actualTotal().subtract(p.bestAvailableTotal()).max(BigDecimal.ZERO)).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal variacaoMedia=resultado.stream().map(ProdutoHistoricoCompra::priceVariationPercent).filter(Objects::nonNull).reduce(BigDecimal.ZERO,BigDecimal::add);
        variacaoMedia=resultado.isEmpty()?BigDecimal.ZERO:dividir(variacaoMedia,resultado.size());
        return new VisaoComparativoCompra(resultado,new ResumoComparativoCompra(resultado.size(),avaliadas,melhores,total,acima,variacaoMedia));
    }

    private ProdutoHistoricoCompra visualizar(ProdutoAcumulado acumulado){
        List<PontoHistoricoCompra> pontos=acumulado.pontos.stream().sorted(Comparator.comparing(PontoHistoricoCompra::purchasedAt).thenComparing(PontoHistoricoCompra::quotationId)).toList();
        BigDecimal primeiro=pontos.getFirst().actualUnitPrice(),ultimo=pontos.getLast().actualUnitPrice(),variacao=ultimo.subtract(primeiro),percentual=primeiro.signum()==0?null:variacao.multiply(BigDecimal.valueOf(100)).divide(primeiro,2,RoundingMode.HALF_UP);
        BigDecimal diferenca=pontos.stream().filter(p->p.bestAvailableTotal()!=null).map(p->p.actualTotal().subtract(p.bestAvailableTotal()).max(BigDecimal.ZERO)).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new ProdutoHistoricoCompra(acumulado.chave,acumulado.ean,acumulado.nome,acumulado.laboratorio,pontos,primeiro,ultimo,variacao,percentual,diferenca,pontos.getLast().priceSituation());
    }

    private Referencia melhorReferencia(ItemCotacao item,int quantidade,List<RespostaCotacao> respostasCotacao){
        List<ItemRespostaCotacao> ofertas=respostasCotacao.stream().filter(r->r.isAtivo()&&r.getStatus()==StatusResposta.SUBMITTED).flatMap(r->r.getItens().stream()).filter(i->i.getItemCotacao().getId().equals(item.getId())&&i.isDisponivel()&&i.getPrecoUnitario()!=null&&i.getPrecoUnitario().signum()>0&&i.getQuantidadeDisponivel()!=null&&i.getQuantidadeDisponivel()>0).sorted(Comparator.comparing(ItemRespostaCotacao::getPrecoUnitario)).toList();
        int restante=quantidade; BigDecimal total=BigDecimal.ZERO;
        for(ItemRespostaCotacao oferta:ofertas){int usar=Math.min(restante,oferta.getQuantidadeDisponivel());total=total.add(oferta.getPrecoUnitario().multiply(BigDecimal.valueOf(usar)));restante-=usar;if(restante==0)break;}
        return restante==0?new Referencia(true,dividir(total,quantidade),total):new Referencia(false,null,null);
    }
    private List<Long> parseIds(String ids){
        if(ids==null||ids.isBlank())throw new RegraNegocioException("Selecione de 2 a 8 cotações para comparar.");
        try{List<Long> valores=Arrays.stream(ids.split(",")).map(String::trim).filter(v->!v.isEmpty()).map(Long::valueOf).distinct().toList();if(valores.size()<2||valores.size()>8)throw new RegraNegocioException("Selecione de 2 a 8 cotações para comparar.");return valores;}catch(NumberFormatException e){throw new RegraNegocioException("As cotações selecionadas são inválidas.");}
    }
    private String chave(ItemCotacao item){String ean=NormalizadorProduto.normalizarEan(item.getProduto().getEan());return ean!=null?"ean:"+ean:"nome:"+NormalizadorProduto.normalizarNome(item.getProduto().getNome())+"|lab:"+NormalizadorProduto.normalizarNome(laboratorio(item));}
    private String laboratorio(ItemCotacao item){return item.getLaboratorioSolicitado()!=null?item.getLaboratorioSolicitado():item.getProduto().getLaboratorio();}
    private BigDecimal dividir(BigDecimal valor,int divisor){return valor.divide(BigDecimal.valueOf(divisor),4,RoundingMode.HALF_UP);}
    private record Referencia(boolean completa,BigDecimal preco,BigDecimal total){}
    private static class ProdutoAcumulado {final String chave,ean,nome,laboratorio;final List<PontoHistoricoCompra> pontos=new ArrayList<>();ProdutoAcumulado(String chave,String ean,String nome,String laboratorio){this.chave=chave;this.ean=ean;this.nome=nome;this.laboratorio=laboratorio;}}
}
