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
    private final LeitorArquivoImportacao parser; private final GeradorToken tokenGenerator;
    private final ComparacaoCotacaoService comparisonService;
    @Value("${app.backend-public-url}") private String backendPublicUrl;

    @Transactional(readOnly = true)
    public PreviaImportacao preview(MultipartFile file) {
        Long companyId = currentUser.companyId(); List<LeitorArquivoImportacao.LinhaBruta> raw = parser.parse(file);
        List<Produto> produtos = productRepository.findAllByEmpresaIdOrderByNome(companyId);
        Map<String, Produto> produtosPorEan = produtos.stream().filter(p -> p.getEan() != null)
            .collect(Collectors.toMap(Produto::getEan, Function.identity()));
        Map<String, List<Produto>> produtosPorNome = produtos.stream()
            .collect(Collectors.groupingBy(p -> NormalizadorProduto.normalizarNome(p.getNome())));
        List<LinhaImportacao> lines = raw.stream().map(line -> validate(line, produtosPorEan, produtosPorNome)).toList();
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
                p.setIdentificadorCatalogo(NormalizadorProduto.identificadorCatalogo(ean, input.productName())); return productRepository.save(p);
            });
            ItemCotacao item = new ItemCotacao(); item.setCotacao(q); item.setProduto(product); item.setQuantidadeSolicitada(input.quantity()); q.getItens().add(item);
        }
        return view(repository.save(q));
    }

    @Transactional(readOnly = true) public List<ResumoCotacao> list() { Long companyId = currentUser.companyId(); return repository.findAllByEmpresaIdOrderByCriadoEmDesc(companyId).stream().map(this::listView).toList(); }
    @Transactional(readOnly = true) public VisaoCotacao get(Long id) { return view(findOwned(id)); }
    @Transactional public VisaoCotacao open(Long id) { Cotacao q = findOwned(id); if (q.getStatus() != StatusCotacao.DRAFT && q.getStatus() != StatusCotacao.CLOSED) throw new RegraNegocioException("Somente cotações em rascunho ou fechadas podem ser abertas."); if (purchaseOrderRepository.existsByCotacaoId(id)) throw new RegraNegocioException("A cotação possui pedidos gerados e não pode ser reaberta."); if (q.getItens().isEmpty()) throw new RegraNegocioException("Inclua produtos antes de abrir a cotação."); if (q.getExpiraEm() != null && !q.getExpiraEm().isAfter(Instant.now())) throw new RegraNegocioException("Atualize o prazo antes de abrir a cotação."); if (q.getTokenPublico() == null) q.setTokenPublico(tokenGenerator.generate()); q.setStatus(StatusCotacao.OPEN); return view(q); }
    @Transactional public VisaoCotacao close(Long id) { Cotacao q = findOwned(id); if (q.getStatus() != StatusCotacao.OPEN) throw new RegraNegocioException("A cotação não está aberta."); q.setStatus(StatusCotacao.CLOSED); return view(q); }
    @Transactional(readOnly = true) public List<VisaoResposta> responses(Long id) { findOwned(id); return responseRepository.findAllByCotacaoEmpresaIdAndCotacaoIdOrderByCriadoEm(currentUser.companyId(), id).stream().map(this::responseView).toList(); }
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
        Map<String, List<Produto>> produtosPorNome) {
        String ean = NormalizadorProduto.normalizarEan(line.ean()); List<String> errors = new ArrayList<>(); Integer quantity = null;
        if (line.ean() != null && !line.ean().isBlank() && (ean == null || !ean.matches("\\d{8,14}")))
            errors.add("EAN deve conter de 8 a 14 dígitos.");
        if (line.name() == null || line.name().isBlank()) errors.add("Nome do produto é obrigatório.");
        try { quantity = Integer.valueOf(line.quantity().replace(".0", "").trim()); if (quantity <= 0) errors.add("Quantidade deve ser maior que zero."); } catch (Exception ex) { errors.add("Quantidade inválida."); }
        Produto product = null;
        if (ean != null) product = produtosPorEan.get(ean);
        else if (line.name() != null && !line.name().isBlank()) {
            List<Produto> candidatos = produtosPorNome.getOrDefault(NormalizadorProduto.normalizarNome(line.name()), List.of());
            List<Produto> candidatosSemEan = candidatos.stream().filter(p -> p.getEan() == null).toList();
            if (candidatosSemEan.size() == 1) product = candidatosSemEan.getFirst();
            else if (candidatos.size() > 1) errors.add("Há mais de um produto com este nome. Informe o EAN para diferenciá-lo.");
            else if (candidatos.size() == 1) product = candidatos.getFirst();
        }
        return new LinhaImportacao(line.row(), ean, line.name() == null ? "" : line.name().trim(), quantity, errors.isEmpty(), product != null,
            product == null ? null : product.getId(), errors);
    }
    private Optional<Produto> localizarProduto(Long empresaId, String ean, String nome) {
        if (ean != null) return productRepository.findByEmpresaIdAndEan(empresaId, ean);
        List<Produto> candidatos = productRepository.findAllByEmpresaIdOrderByNome(empresaId).stream()
            .filter(p -> NormalizadorProduto.normalizarNome(p.getNome()).equals(NormalizadorProduto.normalizarNome(nome))).toList();
        List<Produto> candidatosSemEan = candidatos.stream().filter(p -> p.getEan() == null).toList();
        if (candidatosSemEan.size() == 1) return Optional.of(candidatosSemEan.getFirst());
        if (candidatos.size() > 1) throw new RegraNegocioException("Há mais de um produto com o nome " + nome + ". Informe o EAN.");
        return candidatos.stream().findFirst();
    }
    private ResumoCotacao listView(Cotacao q) { return new ResumoCotacao(q.getId(), q.getNome(), q.getStatus(), q.getExpiraEm(), q.getCriadoEm(), q.getItens().size(), responseRepository.countByCotacaoIdAndStatus(q.getId(), StatusResposta.SUBMITTED)); }
    private VisaoCotacao view(Cotacao q) { return new VisaoCotacao(q.getId(), q.getNome(), q.getStatus(), q.getExpiraEm(), q.getCriadoEm(), q.getAtualizadoEm(), q.getTokenPublico(), q.getTokenPublico() == null ? null : removerBarra(backendPublicUrl) + "/api/publico/cotacoes/" + q.getTokenPublico() + "/compartilhar", q.getItens().stream().map(i -> new VisaoItemCotacao(i.getId(), i.getProduto().getId(), i.getProduto().getEan(), i.getProduto().getNome(), i.getProduto().getLaboratorio(), i.getQuantidadeSolicitada())).toList()); }
    private String removerBarra(String valor) { return valor.endsWith("/") ? valor.substring(0, valor.length() - 1) : valor; }
    private VisaoResposta responseView(RespostaCotacao r) { long count = r.getItens().stream().filter(i -> i.isDisponivel() && i.getPrecoUnitario() != null).count(); BigDecimal total = r.getItens().stream().filter(i -> i.isDisponivel() && i.getPrecoUnitario() != null).map(i -> i.getPrecoUnitario().multiply(BigDecimal.valueOf(Math.min(Optional.ofNullable(i.getQuantidadeDisponivel()).orElse(0), i.getItemCotacao().getQuantidadeSolicitada())))).reduce(BigDecimal.ZERO, BigDecimal::add); return new VisaoResposta(r.getId(), r.getNomeDistribuidora(), r.getNomeRepresentante(), r.getTelefone(), r.getEmail(), r.getStatus(), r.getEnviadoEm(), r.getCriadoEm(), count, total); }
}
