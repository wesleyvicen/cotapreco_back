package br.com.cotapreco.service;

import br.com.cotapreco.dto.ComparacaoDtos.*;
import br.com.cotapreco.enums.StatusResposta;
import br.com.cotapreco.exception.RecursoNaoEncontradoException;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.IntStream;

@Service @RequiredArgsConstructor
public class ComparacaoCotacaoService {
    private final CotacaoRepository repositorioCotacoes;
    private final RespostaCotacaoRepository repositorioRespostas;
    private final EscolhaCompraCotacaoRepository repositorioEscolhas;

    @Transactional(readOnly=true)
    public VisaoComparacao compare(Long cotacaoId,Long empresaId){
        Cotacao cotacao=repositorioCotacoes.findByEmpresaIdAndId(empresaId,cotacaoId)
            .orElseThrow(()->new RecursoNaoEncontradoException("Cotação não encontrada."));
        List<RespostaCotacao> respostas=repositorioRespostas.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(empresaId,cotacaoId)
            .stream().filter(r->r.getStatus()==StatusResposta.SUBMITTED).toList();
        Map<Long,EscolhaCompraCotacao> escolhas=new HashMap<>();
        repositorioEscolhas.findAllByItemCotacaoCotacaoId(cotacaoId).forEach(e->escolhas.put(e.getItemCotacao().getId(),e));
        List<TotalDistribuidor> totais=respostas.stream().map(this::totalDistribuidor).toList();
        Map<Long,Alocacao> alocacoes=new LinkedHashMap<>(); List<ComparacaoProduto> produtos=new ArrayList<>();
        int semOferta=0,coberturaParcial=0;

        for(ItemCotacao itemCotacao:cotacao.getItens()){
            List<ReferenciaOferta> ofertas=respostas.stream().flatMap(resposta->resposta.getItens().stream()
                .filter(item->item.getItemCotacao().getId().equals(itemCotacao.getId())&&ofertaValida(item))
                .map(item->new ReferenciaOferta(resposta,item)))
                .sorted(Comparator.comparing((ReferenciaOferta o)->o.item.getPrecoUnitario()).thenComparing(o->o.resposta.getId())).toList();
            EscolhaCompraCotacao ajuste=escolhas.get(itemCotacao.getId());
            int desejada=ajuste!=null&&ajuste.getQuantidadeDesejada()!=null?ajuste.getQuantidadeDesejada():itemCotacao.getQuantidadeSolicitada();
            boolean usarEscolha=ajuste!=null&&ajuste.getItemRespostaCotacao()!=null&&(ajuste.isCampeaoManual()||ajuste.getQuantidadeCampeao()!=null);
            ReferenciaOferta escolhida=!usarEscolha?null:ofertas.stream()
                .filter(o->o.item.getId().equals(ajuste.getItemRespostaCotacao().getId())).findFirst().orElse(null);
            boolean manual=escolhida!=null&&ajuste.isCampeaoManual();
            boolean manualInvalida=usarEscolha&&escolhida==null;
            ReferenciaOferta campeaCompra=escolhida!=null?escolhida:(ofertas.isEmpty()?null:ofertas.getFirst());
            List<ReferenciaOferta> ordem=new ArrayList<>(); if(campeaCompra!=null)ordem.add(campeaCompra);
            ofertas.stream().filter(o->campeaCompra==null||!o.item.getId().equals(campeaCompra.item.getId())).forEach(ordem::add);
            Integer quantidadeCampeao=ajuste==null?null:ajuste.getQuantidadeCampeao();
            String justificativa=ajuste==null?null:ajuste.getJustificativaEstoque();

            List<OfertaDistribuidor> visaoOfertas=IntStream.range(0,ofertas.size()).mapToObj(i->{ReferenciaOferta o=ofertas.get(i);return new OfertaDistribuidor(
                o.resposta.getId(),o.resposta.getNomeDistribuidora(),o.item.getPrecoUnitario(),o.item.getQuantidadeDisponivel(),
                o.item.getPrecoUnitario().multiply(BigDecimal.valueOf(Math.min(o.item.getQuantidadeDisponivel(),itemCotacao.getQuantidadeSolicitada()))),
                i==0,i+1,manual&&o.item.getId().equals(campeaCompra.item.getId()));}).toList();
            int restante=desejada; boolean primeira=true;
            for(ReferenciaOferta oferta:ordem){if(restante==0)break;int quantidade;
                if(primeira&&quantidadeCampeao!=null)quantidade=Math.min(restante,quantidadeCampeao);
                else quantidade=Math.min(restante,oferta.item.getQuantidadeDisponivel());
                if(quantidade<=0){primeira=false;continue;}
                int posicao=ofertas.indexOf(oferta)+1;BigDecimal subtotal=oferta.item.getPrecoUnitario().multiply(BigDecimal.valueOf(quantidade));
                alocacoes.computeIfAbsent(oferta.resposta.getId(),id->new Alocacao(id,oferta.resposta.getNomeDistribuidora()))
                    .adicionar(new LinhaCompraSugerida(itemCotacao.getId(),itemCotacao.getProduto().getEan(),itemCotacao.getProduto().getNome(),
                        quantidade,oferta.item.getPrecoUnitario(),subtotal,posicao,primeira,!primeira,manual&&primeira,primeira?justificativa:null));
                restante-=quantidade;primeira=false;
            }
            if(desejada>0&&ofertas.isEmpty())semOferta++;else if(restante>0)coberturaParcial++;
            ReferenciaOferta objetiva=ofertas.isEmpty()?null:ofertas.getFirst();
            produtos.add(new ComparacaoProduto(itemCotacao.getId(),itemCotacao.getProduto().getEan(),itemCotacao.getProduto().getNome(),
                itemCotacao.getQuantidadeSolicitada(),desejada,visaoOfertas,objetiva==null?null:objetiva.resposta.getNomeDistribuidora(),
                objetiva==null?null:objetiva.item.getPrecoUnitario(),desejada-restante,restante,
                campeaCompra==null?null:campeaCompra.resposta.getId(),manual,manualInvalida,quantidadeCampeao,
                campeaCompra==null?null:campeaCompra.item.getQuantidadeDisponivel(),justificativa));
        }
        List<CompraSugerida> compra=alocacoes.values().stream().map(Alocacao::visualizar).toList();
        BigDecimal total=compra.stream().map(CompraSugerida::total).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal maior=totais.stream().map(TotalDistribuidor::total).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        return new VisaoComparacao(produtos,totais,compra,semOferta,coberturaParcial,total,maior.subtract(total).max(BigDecimal.ZERO));
    }
    private boolean ofertaValida(ItemRespostaCotacao i){return i.isDisponivel()&&i.getPrecoUnitario()!=null&&i.getPrecoUnitario().signum()>0&&i.getQuantidadeDisponivel()!=null&&i.getQuantidadeDisponivel()>0;}
    private TotalDistribuidor totalDistribuidor(RespostaCotacao r){int quantidade=0;BigDecimal total=BigDecimal.ZERO;for(ItemRespostaCotacao i:r.getItens())if(ofertaValida(i)){quantidade++;total=total.add(i.getPrecoUnitario().multiply(BigDecimal.valueOf(Math.min(i.getQuantidadeDisponivel(),i.getItemCotacao().getQuantidadeSolicitada()))));}return new TotalDistribuidor(r.getId(),r.getNomeDistribuidora(),quantidade,total);}
    private record ReferenciaOferta(RespostaCotacao resposta,ItemRespostaCotacao item){}
    private static class Alocacao{final Long respostaId;final String nome;final List<LinhaCompraSugerida> itens=new ArrayList<>();int quantidade;BigDecimal total=BigDecimal.ZERO;Alocacao(Long id,String nome){this.respostaId=id;this.nome=nome;}void adicionar(LinhaCompraSugerida i){itens.add(i);quantidade+=i.allocatedQuantity();total=total.add(i.subtotal());}CompraSugerida visualizar(){return new CompraSugerida(respostaId,nome,itens.size(),quantidade,total,List.copyOf(itens));}}
}
