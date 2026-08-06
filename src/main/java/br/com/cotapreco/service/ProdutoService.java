package br.com.cotapreco.service;

import br.com.cotapreco.dto.ProdutoDtos.*;
import br.com.cotapreco.exception.*;
import br.com.cotapreco.helper.NormalizadorProduto;
import br.com.cotapreco.model.Produto;
import br.com.cotapreco.repository.ProdutoRepository;
import br.com.cotapreco.security.UsuarioAtualService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service @RequiredArgsConstructor
public class ProdutoService {
    private final ProdutoRepository repositorio;
    private final UsuarioAtualService usuarioAtual;

    @Transactional(readOnly = true)
    public List<VisaoProduto> list() {
        return repositorio.findAllByEmpresaIdOrderByNome(usuarioAtual.companyId()).stream().map(this::visualizar).toList();
    }

    @Transactional
    public VisaoProduto create(SolicitacaoProduto solicitacao) {
        Long empresaId = usuarioAtual.companyId();
        String ean = NormalizadorProduto.normalizarEan(solicitacao.ean());
        validarDisponibilidade(empresaId, null, ean, solicitacao.name());
        Produto produto = new Produto();
        produto.setEmpresa(usuarioAtual.get().getEmpresa());
        aplicar(produto, solicitacao, ean);
        return visualizar(repositorio.save(produto));
    }

    @Transactional
    public VisaoProduto update(Long id, SolicitacaoProduto solicitacao) {
        Long empresaId = usuarioAtual.companyId();
        Produto produto = repositorio.findByEmpresaIdAndId(empresaId, id)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Produto não encontrado."));
        String ean = NormalizadorProduto.normalizarEan(solicitacao.ean());
        validarDisponibilidade(empresaId, id, ean, solicitacao.name());
        aplicar(produto, solicitacao, ean);
        return visualizar(produto);
    }

    private void validarDisponibilidade(Long empresaId, Long idAtual, String ean, String nome) {
        if (ean != null) repositorio.findByEmpresaIdAndEan(empresaId, ean).filter(outro -> !outro.getId().equals(idAtual))
            .ifPresent(outro -> { throw new RegraNegocioException("Já existe um produto com este EAN."); });
        if (ean == null) {
            String nomeNormalizado = NormalizadorProduto.normalizarNome(nome);
            repositorio.findAllByEmpresaIdOrderByNome(empresaId).stream()
                .filter(outro -> !outro.getId().equals(idAtual))
                .filter(outro -> NormalizadorProduto.normalizarNome(outro.getNome()).equals(nomeNormalizado))
                .findFirst().ifPresent(outro -> { throw new RegraNegocioException("Já existe um produto com este nome. Informe o EAN para diferenciá-lo."); });
        }
    }

    private void aplicar(Produto produto, SolicitacaoProduto solicitacao, String ean) {
        produto.setEan(ean);
        produto.setNome(solicitacao.name().trim());
        produto.setIdentificadorCatalogo(NormalizadorProduto.identificadorCatalogo(ean, solicitacao.name()));
        produto.setLaboratorio(limpar(solicitacao.laboratory()));
        produto.setApresentacao(limpar(solicitacao.presentation()));
        produto.setCategoria(limpar(solicitacao.category()));
        if (solicitacao.active() != null) produto.setAtivo(solicitacao.active());
    }

    private String limpar(String valor) { return valor == null || valor.isBlank() ? null : valor.trim(); }
    private VisaoProduto visualizar(Produto produto) {
        return new VisaoProduto(produto.getId(), produto.getEan(), produto.getNome(), produto.getLaboratorio(), produto.getApresentacao(),
            produto.getCategoria(), produto.isAtivo(), produto.getCriadoEm(), produto.getAtualizadoEm());
    }
}
