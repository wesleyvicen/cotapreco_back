package br.com.cotapreco.controller;

import br.com.cotapreco.dto.ProdutoDtos.*;
import br.com.cotapreco.service.ProdutoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/products") @RequiredArgsConstructor
public class ProdutoController {
    private final ProdutoService service;
    @GetMapping public List<VisaoProduto> list() { return service.list(); }
    @PostMapping @PreAuthorize("hasAnyRole('ADMIN','BUYER')") public VisaoProduto create(@Valid @RequestBody SolicitacaoProduto request) { return service.create(request); }
    @PutMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','BUYER')") public VisaoProduto update(@PathVariable Long id, @Valid @RequestBody SolicitacaoProduto request) { return service.update(id, request); }
}
