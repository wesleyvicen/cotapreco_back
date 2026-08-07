package br.com.cotapreco.controller;

import br.com.cotapreco.dto.AutenticacaoDtos.SolicitacaoNovoUsuario;
import br.com.cotapreco.dto.AutenticacaoDtos.VisaoUsuarioAdministracao;
import br.com.cotapreco.service.UsuarioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController @RequestMapping("/api/users") @RequiredArgsConstructor @PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {
    private final UsuarioService servico;

    @GetMapping public List<VisaoUsuarioAdministracao> listar() { return servico.listar(); }
    @PostMapping public VisaoUsuarioAdministracao criar(@Valid @RequestBody SolicitacaoNovoUsuario solicitacao) { return servico.criar(solicitacao); }
}
