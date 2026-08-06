package br.com.cotapreco.service;

import br.com.cotapreco.dto.ProdutoDtos.*;
import br.com.cotapreco.exception.*;
import br.com.cotapreco.model.Produto;
import br.com.cotapreco.repository.ProdutoRepository;
import br.com.cotapreco.security.UsuarioAtualService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
public class ProdutoService {
    private final ProdutoRepository repository; private final UsuarioAtualService currentUser;
    @Transactional(readOnly = true) public List<VisaoProduto> list() { return repository.findAllByEmpresaIdOrderByNome(currentUser.companyId()).stream().map(this::view).toList(); }
    @Transactional public VisaoProduto create(SolicitacaoProduto request) {
        Long companyId = currentUser.companyId(); String gtin = digits(request.gtin());
        if (repository.findByEmpresaIdAndGtin(companyId, gtin).isPresent()) throw new RegraNegocioException("Já existe um produto com este GTIN.");
        Produto p = new Produto(); p.setEmpresa(currentUser.get().getEmpresa()); apply(p, request, gtin); return view(repository.save(p));
    }
    @Transactional public VisaoProduto update(Long id, SolicitacaoProduto request) {
        Long companyId = currentUser.companyId(); Produto p = repository.findByEmpresaIdAndId(companyId, id).orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado."));
        String gtin = digits(request.gtin()); repository.findByEmpresaIdAndGtin(companyId, gtin).filter(other -> !other.getId().equals(id))
            .ifPresent(other -> { throw new RegraNegocioException("Já existe um produto com este GTIN."); });
        apply(p, request, gtin); return view(p);
    }
    private void apply(Produto p, SolicitacaoProduto r, String gtin) { p.setGtin(gtin); p.setNome(r.name().trim()); p.setLaboratorio(clean(r.laboratory())); p.setApresentacao(clean(r.presentation())); p.setCategoria(clean(r.category())); if (r.active() != null) p.setAtivo(r.active()); }
    private String digits(String value) { return value.replaceAll("\\D", ""); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private VisaoProduto view(Produto p) { return new VisaoProduto(p.getId(), p.getGtin(), p.getNome(), p.getLaboratorio(), p.getApresentacao(), p.getCategoria(), p.isAtivo(), p.getCriadoEm(), p.getAtualizadoEm()); }
}
