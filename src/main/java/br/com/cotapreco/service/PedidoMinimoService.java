package br.com.cotapreco.service;

import br.com.cotapreco.dto.ComparacaoDtos.*;
import br.com.cotapreco.dto.PedidoMinimoDtos.*;
import br.com.cotapreco.dto.PlanoCompraDtos.*;
import br.com.cotapreco.enums.StatusCotacao;
import br.com.cotapreco.enums.AcaoHistoricoPlano;
import br.com.cotapreco.exception.*;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import br.com.cotapreco.security.UsuarioAtualService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class PedidoMinimoService {
    private final CotacaoRepository cotacoes;
    private final RespostaCotacaoRepository respostas;
    private final ComparacaoCotacaoService comparacao;
    private final PlanoCompraService planoCompra;
    private final EstadoPedidoCompraService estadoPedidos;
    private final HistoricoPlanoCompraService historico;
    private final UsuarioAtualService usuarioAtual;

    @Transactional(readOnly = true)
    public VisaoOpcoesPedidoMinimo opcoes(Long cotacaoId, Long respostaId) {
        Contexto contexto = contexto(cotacaoId, respostaId);
        return calcularOpcoes(contexto).visao();
    }

    @Transactional
    public ResultadoAplicacaoPedidoMinimo aplicar(Long cotacaoId, Long respostaId, SolicitacaoAplicacaoPedidoMinimo solicitacao) {
        Contexto contexto = contexto(cotacaoId, respostaId);
        exigirFechada(contexto.cotacao());
        CalculoOpcoes calculo = calcularOpcoes(contexto);
        if (solicitacao.strategy() == EstrategiaPedidoMinimo.ATINGIR_MINIMO) {
            if (!contexto.resposta().isIncluidaCompraSugerida())
                throw new RegraNegocioException("Reinclua a distribuidora antes de tentar atingir o valor mínimo.");
            if (!calculo.atingirMinimo().opcao().feasible())
                throw new RegraNegocioException("Não há estoque suficiente nas ofertas para atingir o valor mínimo.");
            VisaoComparacao atualizada = planoCompra.atualizar(cotacaoId,
                new SolicitacaoPlanoCompra(calculo.atingirMinimo().plano().stream().map(Plano::dto).toList()),
                AcaoHistoricoPlano.ATINGIR_MINIMO,"Ajustou o pedido de "+contexto.resposta().getNomeDistribuidora()+" para atingir o mínimo");
            return new ResultadoAplicacaoPedidoMinimo("Plano ajustado para atender o valor mínimo.", atualizada);
        }
        if (!calculo.repassar().opcao().feasible())
            throw new RegraNegocioException("Não é possível repassar o pedido inteiro sem deixar produtos descobertos.");
        historico.preparar(cotacaoId);
        contexto.resposta().setIncluidaCompraSugerida(false);
        respostas.saveAndFlush(contexto.resposta());
        estadoPedidos.invalidar(cotacaoId, usuarioAtual.companyId());
        return new ResultadoAplicacaoPedidoMinimo("Pedido repassado para as próximas ofertas.",
            historico.registrar(cotacaoId,AcaoHistoricoPlano.REPASSAR_PEDIDO,
                "Repassou o pedido de "+contexto.resposta().getNomeDistribuidora()+" para outras distribuidoras"));
    }

    @Transactional
    public VisaoComparacao definirInclusao(Long cotacaoId, Long respostaId, SolicitacaoInclusaoCompra solicitacao) {
        Contexto contexto = contexto(cotacaoId, respostaId);
        exigirFechada(contexto.cotacao());
        if (!solicitacao.included()) {
            return aplicar(cotacaoId, respostaId,
                new SolicitacaoAplicacaoPedidoMinimo(EstrategiaPedidoMinimo.REPASSAR_PEDIDO)).comparison();
        }
        historico.preparar(cotacaoId);
        contexto.resposta().setIncluidaCompraSugerida(true);
        respostas.saveAndFlush(contexto.resposta());
        estadoPedidos.invalidar(cotacaoId, usuarioAtual.companyId());
        return historico.registrar(cotacaoId,AcaoHistoricoPlano.REINCLUIR_DISTRIBUIDORA,
            "Reincluiu "+contexto.resposta().getNomeDistribuidora()+" na compra sugerida");
    }

    private CalculoOpcoes calcularOpcoes(Contexto contexto) {
        VisaoComparacao visao = comparacao.compare(contexto.cotacao().getId(), usuarioAtual.companyId());
        CompraSugerida grupo = visao.suggestedPurchase().stream()
            .filter(item -> item.responseId().equals(contexto.resposta().getId())).findFirst().orElse(null);
        BigDecimal atual = grupo == null ? BigDecimal.ZERO : grupo.total();
        BigDecimal minimo = contexto.resposta().getValorMinimoPedido();
        if (minimo == null) throw new RegraNegocioException("Esta distribuidora não informou valor mínimo de pedido.");
        BigDecimal falta = minimo.subtract(atual).max(BigDecimal.ZERO);
        List<Plano> planoInicial = visao.products().stream().map(Plano::novo).toList();
        Set<Long> incluidas = visao.supplierTotals().stream().filter(TotalDistribuidor::includedInSuggestedPurchase)
            .map(TotalDistribuidor::responseId).collect(Collectors.toCollection(LinkedHashSet::new));
        Simulacao simulacaoInicial = simular(visao.products(), planoInicial, incluidas);
        ResultadoOpcao atingir = atingirMinimo(visao, contexto.resposta(), planoInicial, incluidas, simulacaoInicial);
        ResultadoOpcao repassar = repassar(visao, contexto.resposta(), planoInicial, incluidas, simulacaoInicial);
        return new CalculoOpcoes(new VisaoOpcoesPedidoMinimo(contexto.resposta().getId(), contexto.resposta().getNomeDistribuidora(),
            atual, minimo, falta, atingir.opcao(), repassar.opcao()), atingir, repassar);
    }

    private ResultadoOpcao atingirMinimo(VisaoComparacao visao, RespostaCotacao alvo, List<Plano> inicial,
        Set<Long> incluidas, Simulacao base) {
        if (!alvo.isIncluidaCompraSugerida()) return inviavel(inicial, base);
        BigDecimal minimo = alvo.getValorMinimoPedido();
        if (total(base, alvo.getId()).compareTo(minimo) >= 0)
            return resultado(inicial, inicial, visao, alvo.getId(), base, base, true, 0);

        List<Plano> atual = copiar(inicial);
        Simulacao simulacao = base;
        Set<Long> protegidas = visao.supplierTotals().stream()
            .filter(t -> !t.responseId().equals(alvo.getId()) && t.minimumOrderValue() != null
                && total(base, t.responseId()).compareTo(t.minimumOrderValue()) >= 0)
            .map(TotalDistribuidor::responseId).collect(Collectors.toSet());
        Map<Long, BigDecimal> minimos = visao.supplierTotals().stream().filter(t -> t.minimumOrderValue() != null)
            .collect(Collectors.toMap(TotalDistribuidor::responseId, TotalDistribuidor::minimumOrderValue));

        for (int passo = 0; passo < visao.products().size() * 2 && total(simulacao, alvo.getId()).compareTo(minimo) < 0; passo++) {
            Candidato melhor = null;
            BigDecimal falta = minimo.subtract(total(simulacao, alvo.getId()));
            for (ComparacaoProduto produto : visao.products()) {
                OfertaDistribuidor oferta = oferta(produto, alvo.getId());
                if (oferta == null) continue;
                int atualAlvo = quantidade(simulacao, produto.quotationItemId(), alvo.getId());
                Plano plano = localizar(atual, produto.quotationItemId());
                int limite = Math.min(plano.quantidadeDesejada, oferta.availableQuantity());
                int capacidade = limite - atualAlvo;
                if (capacidade <= 0) continue;
                int quantidade = Math.min(capacidade, unidadesPara(falta, oferta.unitPrice()));
                List<Plano> tentativa = copiar(atual);
                substituir(tentativa, plano.comDistribuidora(alvo.getId(), atualAlvo + quantidade));
                Simulacao simulada = simular(visao.products(), tentativa, incluidas);
                BigDecimal ganho = total(simulada, alvo.getId()).subtract(total(simulacao, alvo.getId()));
                if (ganho.signum() <= 0 || violaMinimo(simulada, protegidas, minimos)) continue;
                Candidato candidato = new Candidato(tentativa, simulada, ganho,
                    simulada.total().subtract(simulacao.total()), quantidade);
                if (melhor == null || candidato.melhorQue(melhor)) melhor = candidato;
            }
            if (melhor == null) break;
            atual = melhor.plano(); simulacao = melhor.simulacao();
        }

        int extras = 0;
        for (int passo = 0; passo < visao.products().size() * 2 && total(simulacao, alvo.getId()).compareTo(minimo) < 0; passo++) {
            Candidato melhor = null;
            BigDecimal falta = minimo.subtract(total(simulacao, alvo.getId()));
            for (ComparacaoProduto produto : visao.products()) {
                OfertaDistribuidor oferta = oferta(produto, alvo.getId());
                if (oferta == null) continue;
                int atualAlvo = quantidade(simulacao, produto.quotationItemId(), alvo.getId());
                int capacidade = oferta.availableQuantity() - atualAlvo;
                if (capacidade <= 0) continue;
                int quantidade = Math.min(capacidade, unidadesPara(falta, oferta.unitPrice()));
                Plano plano = localizar(atual, produto.quotationItemId());
                List<Plano> tentativa = copiar(atual);
                substituir(tentativa, plano.comExtra(alvo.getId(), atualAlvo + quantidade, quantidade));
                Simulacao simulada = simular(visao.products(), tentativa, incluidas);
                BigDecimal ganho = total(simulada, alvo.getId()).subtract(total(simulacao, alvo.getId()));
                if (ganho.signum() <= 0) continue;
                Candidato candidato = new Candidato(tentativa, simulada, ganho,
                    simulada.total().subtract(simulacao.total()), quantidade);
                if (melhor == null || candidato.melhorQue(melhor)) melhor = candidato;
            }
            if (melhor == null) break;
            atual = melhor.plano(); simulacao = melhor.simulacao(); extras += melhor.quantidade();
        }
        boolean viavel = total(simulacao, alvo.getId()).compareTo(minimo) >= 0 && !violaMinimo(simulacao, protegidas, minimos);
        return resultado(inicial, atual, visao, alvo.getId(), base, simulacao, viavel, extras);
    }

    private ResultadoOpcao repassar(VisaoComparacao visao, RespostaCotacao alvo, List<Plano> inicial,
        Set<Long> incluidas, Simulacao base) {
        Set<Long> semAlvo = new LinkedHashSet<>(incluidas); semAlvo.remove(alvo.getId());
        Simulacao simulada = simular(visao.products(), inicial, semAlvo);
        int novasNaoCobertas = Math.max(0, simulada.naoCobertas() - base.naoCobertas());
        boolean viavel = novasNaoCobertas == 0;
        List<AjustePedidoMinimo> ajustes = new ArrayList<>();
        for (ComparacaoProduto produto : visao.products()) {
            int quantidadeAtual = quantidade(base, produto.quotationItemId(), alvo.getId());
            if (quantidadeAtual == 0) continue;
            String destinos = simulada.linhas(produto.quotationItemId()).stream()
                .map(LinhaSimulada::supplierName).distinct().collect(Collectors.joining(", "));
            OfertaDistribuidor oferta = oferta(produto, alvo.getId());
            ajustes.add(new AjustePedidoMinimo(produto.quotationItemId(), produto.productName(),
                TipoAjustePedidoMinimo.REPASSE, quantidadeAtual, 0, 0,
                oferta == null ? BigDecimal.ZERO : oferta.unitPrice(), destinos.isBlank() ? null : destinos));
        }
        OpcaoPedidoMinimo opcao = new OpcaoPedidoMinimo(viavel, BigDecimal.ZERO, simulada.total(),
            simulada.total().subtract(base.total()), 0, novasNaoCobertas, ajustes);
        return new ResultadoOpcao(opcao, inicial);
    }

    private ResultadoOpcao resultado(List<Plano> inicial, List<Plano> finalizado, VisaoComparacao visao, Long alvo,
        Simulacao base, Simulacao projetada, boolean viavel, int extras) {
        List<AjustePedidoMinimo> ajustes = new ArrayList<>();
        for (ComparacaoProduto produto : visao.products()) {
            int antes = quantidade(base, produto.quotationItemId(), alvo);
            int depois = quantidade(projetada, produto.quotationItemId(), alvo);
            Plano original = localizar(inicial, produto.quotationItemId());
            Plano alterado = localizar(finalizado, produto.quotationItemId());
            int extra = Math.max(0, alterado.quantidadeDesejada - original.quantidadeDesejada);
            if (antes == depois && extra == 0) continue;
            OfertaDistribuidor oferta = oferta(produto, alvo);
            ajustes.add(new AjustePedidoMinimo(produto.quotationItemId(), produto.productName(),
                extra > 0 ? TipoAjustePedidoMinimo.UNIDADES_EXTRAS : TipoAjustePedidoMinimo.REALOCACAO,
                antes, depois, extra, oferta == null ? BigDecimal.ZERO : oferta.unitPrice(), null));
        }
        OpcaoPedidoMinimo opcao = new OpcaoPedidoMinimo(viavel, total(projetada, alvo), projetada.total(),
            projetada.total().subtract(base.total()), extras, projetada.naoCobertas(), ajustes);
        return new ResultadoOpcao(opcao, finalizado);
    }

    private ResultadoOpcao inviavel(List<Plano> plano, Simulacao base) {
        return new ResultadoOpcao(new OpcaoPedidoMinimo(false, BigDecimal.ZERO, base.total(),
            BigDecimal.ZERO, 0, base.naoCobertas(), List.of()), plano);
    }

    private Simulacao simular(List<ComparacaoProduto> produtos, List<Plano> planos, Set<Long> incluidas) {
        Map<Long, List<LinhaSimulada>> linhas = new LinkedHashMap<>();
        Map<Long, BigDecimal> totais = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO; int naoCobertas = 0;
        for (ComparacaoProduto produto : produtos) {
            Plano plano = localizar(planos, produto.quotationItemId());
            List<OfertaDistribuidor> ofertas = produto.offers().stream().filter(o -> incluidas.contains(o.responseId())).toList();
            if (plano.quantidadeDesejada == 0) { linhas.put(produto.quotationItemId(), List.of()); continue; }
            OfertaDistribuidor principal = ofertas.stream().filter(o -> Objects.equals(o.responseId(), plano.respostaId)).findFirst()
                .orElse(ofertas.isEmpty() ? null : ofertas.getFirst());
            List<OfertaDistribuidor> ordem = new ArrayList<>(); if (principal != null) ordem.add(principal);
            ofertas.stream().filter(o -> principal == null || !o.responseId().equals(principal.responseId())).forEach(ordem::add);
            int restante = plano.quantidadeDesejada; boolean primeira = true; List<LinhaSimulada> linhasProduto = new ArrayList<>();
            for (OfertaDistribuidor oferta : ordem) {
                if (restante == 0) break;
                int quantidade = primeira && plano.quantidadeCampea != null
                    ? Math.min(restante, plano.quantidadeCampea) : Math.min(restante, oferta.availableQuantity());
                primeira = false; if (quantidade <= 0) continue;
                BigDecimal subtotal = oferta.unitPrice().multiply(BigDecimal.valueOf(quantidade));
                linhasProduto.add(new LinhaSimulada(oferta.responseId(), oferta.supplierName(), quantidade, oferta.unitPrice(), subtotal));
                totais.merge(oferta.responseId(), subtotal, BigDecimal::add); total = total.add(subtotal); restante -= quantidade;
            }
            naoCobertas += restante; linhas.put(produto.quotationItemId(), List.copyOf(linhasProduto));
        }
        return new Simulacao(linhas, totais, total, naoCobertas);
    }

    private Contexto contexto(Long cotacaoId, Long respostaId) {
        Cotacao cotacao = cotacoes.findByEmpresaIdAndId(usuarioAtual.companyId(), cotacaoId)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Cotação não encontrada."));
        RespostaCotacao resposta = respostas.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(usuarioAtual.companyId(), cotacaoId)
            .stream().filter(r -> r.getId().equals(respostaId)).findFirst()
            .orElseThrow(() -> new RecursoNaoEncontradoException("Resposta não encontrada."));
        return new Contexto(cotacao, resposta);
    }

    private void exigirFechada(Cotacao cotacao) {
        if (cotacao.getStatus() != StatusCotacao.CLOSED)
            throw new RegraNegocioException("As alternativas de pedido mínimo só podem ser aplicadas com a cotação fechada.");
    }
    private boolean violaMinimo(Simulacao simulacao, Set<Long> protegidas, Map<Long, BigDecimal> minimos) {
        return protegidas.stream().anyMatch(id -> total(simulacao, id).compareTo(minimos.get(id)) < 0);
    }
    private BigDecimal total(Simulacao simulacao, Long respostaId) { return simulacao.totais().getOrDefault(respostaId, BigDecimal.ZERO); }
    private int quantidade(Simulacao simulacao, Long itemId, Long respostaId) { return simulacao.linhas(itemId).stream().filter(l -> l.responseId().equals(respostaId)).mapToInt(LinhaSimulada::quantity).sum(); }
    private OfertaDistribuidor oferta(ComparacaoProduto produto, Long respostaId) { return produto.offers().stream().filter(o -> o.responseId().equals(respostaId)).findFirst().orElse(null); }
    private int unidadesPara(BigDecimal valor, BigDecimal preco) { return valor.divide(preco, 0, RoundingMode.CEILING).max(BigDecimal.ONE).intValue(); }
    private List<Plano> copiar(List<Plano> planos) { return planos.stream().map(Plano::copia).collect(Collectors.toCollection(ArrayList::new)); }
    private Plano localizar(List<Plano> planos, Long itemId) { return planos.stream().filter(p -> p.itemId.equals(itemId)).findFirst().orElseThrow(); }
    private void substituir(List<Plano> planos, Plano novo) { planos.replaceAll(p -> p.itemId.equals(novo.itemId) ? novo : p); }

    private record Contexto(Cotacao cotacao, RespostaCotacao resposta) {}
    private record CalculoOpcoes(VisaoOpcoesPedidoMinimo visao, ResultadoOpcao atingirMinimo, ResultadoOpcao repassar) {}
    private record ResultadoOpcao(OpcaoPedidoMinimo opcao, List<Plano> plano) {}
    private record LinhaSimulada(Long responseId, String supplierName, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {}
    private record Simulacao(Map<Long,List<LinhaSimulada>> porItem, Map<Long,BigDecimal> totais, BigDecimal total, int naoCobertas) {
        List<LinhaSimulada> linhas(Long itemId) { return porItem.getOrDefault(itemId, List.of()); }
    }
    private record Candidato(List<Plano> plano, Simulacao simulacao, BigDecimal ganho, BigDecimal custo, int quantidade) {
        boolean melhorQue(Candidato outro) {
            BigDecimal indice = custo.divide(ganho, 8, RoundingMode.HALF_UP);
            BigDecimal indiceOutro = outro.custo.divide(outro.ganho, 8, RoundingMode.HALF_UP);
            int comparacao = indice.compareTo(indiceOutro);
            return comparacao < 0 || comparacao == 0 && quantidade < outro.quantidade;
        }
    }
    private static final class Plano {
        private final Long itemId; private final int quantidadeDesejada; private final Long respostaId;
        private final Integer quantidadeCampea; private final String justificativa; private final boolean manual;
        private Plano(Long itemId,int quantidadeDesejada,Long respostaId,Integer quantidadeCampea,String justificativa,boolean manual){this.itemId=itemId;this.quantidadeDesejada=quantidadeDesejada;this.respostaId=respostaId;this.quantidadeCampea=quantidadeCampea;this.justificativa=justificativa;this.manual=manual;}
        static Plano novo(ComparacaoProduto produto){return new Plano(produto.quotationItemId(),produto.desiredQuantity(),produto.selectedResponseId(),produto.championQuantity(),produto.stockOverrideNote(),produto.manualSelection());}
        Plano copia(){return new Plano(itemId,quantidadeDesejada,respostaId,quantidadeCampea,justificativa,manual);}
        Plano comDistribuidora(Long resposta,int quantidade){return new Plano(itemId,quantidadeDesejada,resposta,quantidade,null,true);}
        Plano comExtra(Long resposta,int quantidade,int extra){return new Plano(itemId,quantidadeDesejada+extra,resposta,quantidade,null,true);}
        ItemPlanoCompra dto(){return new ItemPlanoCompra(itemId,quantidadeDesejada,respostaId,quantidadeCampea,justificativa,manual);}
    }
}
