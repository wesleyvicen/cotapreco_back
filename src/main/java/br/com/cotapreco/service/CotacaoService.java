package br.com.cotapreco.service;

import br.com.cotapreco.dto.CotacaoDtos.*;
import br.com.cotapreco.enums.*;
import br.com.cotapreco.exception.*;
import br.com.cotapreco.helper.*;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import br.com.cotapreco.security.UsuarioAtualService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class CotacaoService {
    private final CotacaoRepository repository; private final ProdutoRepository productRepository;
    private final RespostaCotacaoRepository responseRepository; private final UsuarioAtualService currentUser;
    private final PedidoCompraRepository purchaseOrderRepository;
    private final EstadoPedidoCompraService estadoPedidos;
    private final LeitorArquivoImportacao parser; private final GeradorToken tokenGenerator;
    private final ComparacaoCotacaoService comparisonService;
    @Value("${app.share-public-url:${app.backend-public-url}}") private String urlPublicaCompartilhamento;

    @Transactional(readOnly = true)
    public AnaliseArquivoImportacao analyzeImport(MultipartFile file) {
        var analysis = parser.analisar(file);
        var mapping = analysis.suggestedMapping();
        return new AnaliseArquivoImportacao(analysis.sheetName(), analysis.totalRows(),
            analysis.columns().stream().map(column -> new ColunaArquivo(column.index(), column.name())).toList(),
            new MapeamentoColunas(mapping.ean(), mapping.productName(), mapping.quantity(), mapping.laboratory()), analysis.sampleRows());
    }

    @Transactional(readOnly = true)
    public PreviaImportacao preview(MultipartFile file) { return preview(file, null); }

    @Transactional(readOnly = true)
    public PreviaImportacao preview(MultipartFile file, MapeamentoColunas mapping) {
        var mapped = mapping == null ? null : new LeitorArquivoImportacao.MapeamentoArquivo(mapping.ean(), mapping.productName(), mapping.quantity(), mapping.laboratory());
        return previewLines(parser.parse(file, mapped));
    }

    @Transactional(readOnly = true)
    public PreviaImportacao previewManual(SolicitacaoPreviaManual request) {
        List<LeitorArquivoImportacao.LinhaBruta> lines = new ArrayList<>();
        for (int index = 0; index < request.items().size(); index++) {
            ItemPreviaManual item = request.items().get(index);
            lines.add(new LeitorArquivoImportacao.LinhaBruta(item.row() == null ? index + 1 : item.row(), item.ean(),
                item.productName(), item.quantity(), item.laboratory()));
        }
        return previewLines(lines);
    }

    private PreviaImportacao previewLines(List<LeitorArquivoImportacao.LinhaBruta> raw) {
        Long companyId = currentUser.companyId();
        List<Produto> produtos = productRepository.findAllByEmpresaIdOrderByNome(companyId);
        Map<String, Produto> produtosPorEan = produtos.stream().filter(p -> p.getEan() != null)
            .collect(Collectors.toMap(Produto::getEan, Function.identity()));
        Map<String, List<Produto>> produtosPorNome = produtos.stream()
            .collect(Collectors.groupingBy(p -> NormalizadorProduto.normalizarNome(p.getNome())));
        Map<String, Long> occurrences = raw.stream().filter(line -> line.name() != null && !line.name().isBlank())
            .collect(Collectors.groupingBy(this::importIdentifier, Collectors.counting()));
        List<LinhaImportacao> lines = raw.stream()
            .map(line -> validate(line, produtosPorEan, produtosPorNome, occurrences.getOrDefault(importIdentifier(line), 0L) > 1)).toList();
        int valid = (int) lines.stream().filter(LinhaImportacao::valid).count();
        return new PreviaImportacao(lines.size(), valid, lines.size() - valid, lines);
    }

    @Transactional
    public VisaoCotacao create(SolicitacaoCriacaoCotacao request) {
        Usuario user = currentUser.get();
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) throw new RegraNegocioException("O prazo deve estar no futuro.");
        Set<String> unique = new HashSet<>();
        for (var item : request.items()) {
            String ean = NormalizadorProduto.normalizarEan(item.ean());
            String identificador = NormalizadorProduto.identificadorCatalogo(ean, item.productName());
            if (!unique.add(identificador)) throw new RegraNegocioException("A lista contém produto duplicado: " + item.productName());
        }
        Cotacao q = new Cotacao(); q.setEmpresa(user.getEmpresa()); q.setCriadoPor(user); q.setNome(request.name().trim()); q.setExpiraEm(request.expiresAt());
        for (var input : request.items()) {
            String ean = NormalizadorProduto.normalizarEan(input.ean());
            Produto product = localizarProduto(user.getEmpresa().getId(), ean, input.productName()).orElseGet(() -> {
                Produto p = new Produto(); p.setEmpresa(user.getEmpresa()); p.setEan(ean); p.setNome(input.productName().trim());
                p.setLaboratorio(clean(input.laboratory()));
                p.setIdentificadorCatalogo(NormalizadorProduto.identificadorCatalogo(ean, input.productName())); return productRepository.save(p);
            });
            ItemCotacao item = new ItemCotacao(); item.setCotacao(q); item.setProduto(product); item.setQuantidadeSolicitada(input.quantity()); item.setLaboratorioSolicitado(clean(input.laboratory())); q.getItens().add(item);
        }
        return view(repository.save(q));
    }

    @Transactional(readOnly = true) public List<ResumoCotacao> list() { Long companyId = currentUser.companyId();
        Map<Long,List<PedidoCompra>> pedidosAtuais=purchaseOrderRepository.findAllByCotacaoEmpresaIdAndStatusIn(companyId,List.of(StatusPedidoCompra.GERADO,StatusPedidoCompra.COMPARTILHADO)).stream().collect(Collectors.groupingBy(p->p.getCotacao().getId()));
        return repository.findAllByEmpresaIdOrderByCriadoEmDesc(companyId).stream().map(q->listView(q,pedidosAtuais.getOrDefault(q.getId(),List.of()))).toList(); }
    @Transactional(readOnly = true) public VisaoCotacao get(Long id) { return view(findOwned(id)); }
    @Transactional public VisaoItemCotacao atualizarItem(Long cotacaoId, Long itemId, SolicitacaoAtualizacaoItemCotacao request) {
        Cotacao cotacao = findOwned(cotacaoId);
        if (cotacao.getStatus() != StatusCotacao.DRAFT && cotacao.getStatus() != StatusCotacao.OPEN && cotacao.getStatus() != StatusCotacao.CLOSED)
            throw new RegraNegocioException("Produtos só podem ser alterados em cotações em rascunho, abertas ou fechadas.");
        ItemCotacao item = cotacao.getItens().stream().filter(i -> i.getId().equals(itemId)).findFirst()
            .orElseThrow(() -> new RecursoNaoEncontradoException("Item da cotação não encontrado."));
        boolean estavaAtivo = item.isAtivo();
        item.setQuantidadeSolicitada(request.quantity());
        item.setAtivo(request.active());
        if (!estavaAtivo && request.active()) responseRepository.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(currentUser.companyId(), cotacaoId).forEach(resposta -> {
            if (resposta.getItens().stream().noneMatch(itemResposta -> itemResposta.getItemCotacao().getId().equals(itemId))) {
                ItemRespostaCotacao itemResposta = new ItemRespostaCotacao();
                itemResposta.setRespostaCotacao(resposta); itemResposta.setItemCotacao(item); resposta.getItens().add(itemResposta);
            }
        });
        estadoPedidos.invalidar(cotacaoId, currentUser.companyId());
        return itemView(item);
    }
    @Transactional public VisaoCotacao open(Long id) { Cotacao q = findOwned(id); if (q.getStatus() != StatusCotacao.DRAFT && q.getStatus() != StatusCotacao.CLOSED) throw new RegraNegocioException("Somente cotações em rascunho ou fechadas podem ser abertas."); if (purchaseOrderRepository.existsByCotacaoId(id)) throw new RegraNegocioException("A cotação possui pedidos gerados e não pode ser reaberta."); if (q.getItens().stream().noneMatch(ItemCotacao::isAtivo)) throw new RegraNegocioException("Ative ao menos um produto antes de abrir a cotação."); if (q.getExpiraEm() != null && !q.getExpiraEm().isAfter(Instant.now())) throw new RegraNegocioException("Atualize o prazo antes de abrir a cotação."); if (q.getTokenPublico() == null) q.setTokenPublico(tokenGenerator.generate()); q.setStatus(StatusCotacao.OPEN); return view(q); }
    @Transactional public VisaoCotacao close(Long id) { Cotacao q = findOwned(id); if (q.getStatus() != StatusCotacao.OPEN) throw new RegraNegocioException("A cotação não está aberta."); q.setStatus(StatusCotacao.CLOSED); return view(q); }
    @Transactional public VisaoCotacao prorrogar(Long id, SolicitacaoProrrogacaoCotacao request) {
        Cotacao cotacao = findOwned(id);
        if (!request.expiresAt().isAfter(Instant.now())) throw new RegraNegocioException("O novo prazo deve estar no futuro.");
        if (cotacao.getStatus() != StatusCotacao.OPEN && cotacao.getStatus() != StatusCotacao.CLOSED)
            throw new RegraNegocioException("Somente cotações abertas ou fechadas podem ser prorrogadas.");
        if (cotacao.getStatus() == StatusCotacao.CLOSED && purchaseOrderRepository.existsByCotacaoId(id))
            throw new RegraNegocioException("A cotação possui pedidos gerados e não pode ser reaberta.");
        cotacao.setExpiraEm(request.expiresAt());
        cotacao.setStatus(StatusCotacao.OPEN);
        return view(cotacao);
    }
    @Transactional(readOnly = true) public List<VisaoResposta> responses(Long id) { findOwned(id); return responseRepository.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(currentUser.companyId(), id).stream().map(this::responseView).toList(); }
    @Transactional public VisaoResposta atualizarRespostaAtiva(Long cotacaoId, Long respostaId, SolicitacaoAtivacaoResposta request) {
        Cotacao cotacao = findOwned(cotacaoId);
        if (cotacao.getStatus() != StatusCotacao.DRAFT && cotacao.getStatus() != StatusCotacao.OPEN && cotacao.getStatus() != StatusCotacao.CLOSED)
            throw new RegraNegocioException("Respostas só podem ser alteradas em cotações em rascunho, abertas ou fechadas.");
        RespostaCotacao resposta = responseRepository.findById(respostaId).filter(r -> r.getCotacao().getId().equals(cotacaoId))
            .orElseThrow(() -> new RecursoNaoEncontradoException("Resposta não encontrada."));
        resposta.setAtivo(request.active());
        estadoPedidos.invalidar(cotacaoId, currentUser.companyId());
        return responseView(resposta);
    }
    @Transactional(readOnly = true) public VisaoPainel dashboard() {
        Long companyId = currentUser.companyId(); List<ResumoCotacao> latest = list().stream().limit(6).toList();
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC); Instant start = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = now.with(TemporalAdjusters.lastDayOfMonth()).toLocalDate().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal quoted = BigDecimal.ZERO, savings = BigDecimal.ZERO;
        for (var q : repository.findAllByEmpresaIdOrderByCriadoEmDesc(companyId)) { var c = comparisonService.compare(q.getId(), companyId); quoted = quoted.add(c.supplierTotals().stream().map(s -> s.total()).reduce(BigDecimal.ZERO, BigDecimal::add)); savings = savings.add(c.estimatedSavings()); }
        return new VisaoPainel(repository.countByEmpresaIdAndStatus(companyId, StatusCotacao.OPEN),
            repository.countByEmpresaIdAndStatus(companyId, StatusCotacao.COMPLETED) + repository.countByEmpresaIdAndStatus(companyId, StatusCotacao.CLOSED),
            responseRepository.countByCotacaoEmpresaIdAndStatusAndEnviadoEmBetween(companyId, StatusResposta.SUBMITTED, start, end), quoted, savings, latest);
    }
    public Cotacao findOwned(Long id) { return repository.findByEmpresaIdAndId(currentUser.companyId(), id).orElseThrow(() -> new RecursoNaoEncontradoException("Cotação não encontrada.")); }
    private LinhaImportacao validate(LeitorArquivoImportacao.LinhaBruta line, Map<String, Produto> produtosPorEan,
        Map<String, List<Produto>> produtosPorNome, boolean duplicate) {
        String ean = NormalizadorProduto.normalizarEan(line.ean()); List<String> errors = new ArrayList<>(); Integer quantity = null;
        if (line.ean() != null && !line.ean().isBlank() && (ean == null || !ean.matches("\\d{8,14}")))
            errors.add("EAN deve conter de 8 a 14 dígitos.");
        if (line.name() == null || line.name().isBlank()) errors.add("Nome do produto é obrigatório.");
        else if (line.name().trim().length() > 240) errors.add("Nome do produto deve ter no máximo 240 caracteres.");
        try { quantity = new BigDecimal(line.quantity().trim().replace(',', '.')).intValueExact(); if (quantity <= 0) errors.add("Quantidade deve ser maior que zero."); } catch (Exception ex) { errors.add("Quantidade inválida."); }
        String laboratory = clean(line.laboratory());
        if (laboratory != null && laboratory.length() > 160) errors.add("Laboratório deve ter no máximo 160 caracteres.");
        if (duplicate) errors.add("Produto duplicado na lista.");
        Produto product = null;
        if (ean != null) product = produtosPorEan.get(ean);
        else if (line.name() != null && !line.name().isBlank()) {
            List<Produto> candidatos = produtosPorNome.getOrDefault(NormalizadorProduto.normalizarNome(line.name()), List.of());
            List<Produto> candidatosSemEan = candidatos.stream().filter(p -> p.getEan() == null).toList();
            if (candidatosSemEan.size() == 1) product = candidatosSemEan.getFirst();
            else if (candidatos.size() > 1) errors.add("Há mais de um produto com este nome. Informe o EAN para diferenciá-lo.");
            else if (candidatos.size() == 1) product = candidatos.getFirst();
        }
        return new LinhaImportacao(line.row(), ean, line.name() == null ? "" : line.name().trim(), quantity, laboratory, errors.isEmpty(), product != null,
            product == null ? null : product.getId(), errors);
    }
    private String importIdentifier(LeitorArquivoImportacao.LinhaBruta line) {
        String ean = NormalizadorProduto.normalizarEan(line.ean());
        return NormalizadorProduto.identificadorCatalogo(ean, line.name());
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private Optional<Produto> localizarProduto(Long empresaId, String ean, String nome) {
        if (ean != null) return productRepository.findByEmpresaIdAndEan(empresaId, ean);
        List<Produto> candidatos = productRepository.findAllByEmpresaIdOrderByNome(empresaId).stream()
            .filter(p -> NormalizadorProduto.normalizarNome(p.getNome()).equals(NormalizadorProduto.normalizarNome(nome))).toList();
        List<Produto> candidatosSemEan = candidatos.stream().filter(p -> p.getEan() == null).toList();
        if (candidatosSemEan.size() == 1) return Optional.of(candidatosSemEan.getFirst());
        if (candidatos.size() > 1) throw new RegraNegocioException("Há mais de um produto com o nome " + nome + ". Informe o EAN.");
        return candidatos.stream().findFirst();
    }
    private ResumoCotacao listView(Cotacao q,List<PedidoCompra> pedidosAtuais) { int itensComprados=pedidosAtuais.stream().mapToInt(p->p.getItens().size()).sum(); Instant ultimaCompra=pedidosAtuais.stream().map(PedidoCompra::getGeradoEm).max(Instant::compareTo).orElse(null); return new ResumoCotacao(q.getId(), q.getNome(), q.getStatus(), q.getExpiraEm(), q.getCriadoEm(), (int) q.getItens().stream().filter(ItemCotacao::isAtivo).count(), responseRepository.countByCotacaoIdAndStatus(q.getId(), StatusResposta.SUBMITTED), itensComprados>0,itensComprados,ultimaCompra); }
    private VisaoCotacao view(Cotacao q) { return new VisaoCotacao(q.getId(), q.getNome(), q.getStatus(), q.getExpiraEm(), q.getCriadoEm(), q.getAtualizadoEm(), q.getTokenPublico(), q.getTokenPublico() == null ? null : removerBarra(urlPublicaCompartilhamento) + "/api/publico/cotacoes/" + q.getTokenPublico() + "/compartilhar" + versaoCompartilhamento(q), q.getItens().stream().map(this::itemView).toList()); }
    private String versaoCompartilhamento(Cotacao cotacao) { return cotacao.getExpiraEm() == null ? "" : "?v=" + cotacao.getExpiraEm().toEpochMilli(); }
    private VisaoItemCotacao itemView(ItemCotacao i) { return new VisaoItemCotacao(i.getId(), i.getProduto().getId(), i.getProduto().getEan(), i.getProduto().getNome(), laboratorio(i), i.getQuantidadeSolicitada(), i.isAtivo()); }
    private String laboratorio(ItemCotacao item) { return item.getLaboratorioSolicitado() != null ? item.getLaboratorioSolicitado() : item.getProduto().getLaboratorio(); }
    private String removerBarra(String valor) { return valor.endsWith("/") ? valor.substring(0, valor.length() - 1) : valor; }
    private VisaoResposta responseView(RespostaCotacao r) { long count = r.getItens().stream().filter(i -> i.getItemCotacao().isAtivo() && i.isDisponivel() && i.getPrecoUnitario() != null).count(); BigDecimal total = r.getItens().stream().filter(i -> i.getItemCotacao().isAtivo() && i.isDisponivel() && i.getPrecoUnitario() != null).map(i -> i.getPrecoUnitario().multiply(BigDecimal.valueOf(Math.min(Optional.ofNullable(i.getQuantidadeDisponivel()).orElse(0), i.getItemCotacao().getQuantidadeSolicitada())))).reduce(BigDecimal.ZERO, BigDecimal::add); return new VisaoResposta(r.getId(), r.getNomeDistribuidora(), r.getNomeRepresentante(), r.getTelefone(), r.getEmail(), r.getStatus(), r.getEnviadoEm(), r.getCriadoEm(), count, total, r.getValorMinimoPedido(), r.isIncluidaCompraSugerida(), r.isAtivo()); }
}
