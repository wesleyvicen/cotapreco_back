package br.com.cotapreco.controller;

import br.com.cotapreco.dto.AutenticacaoDtos.*;
import br.com.cotapreco.security.UsuarioAtualService;
import br.com.cotapreco.service.AutenticacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AutenticacaoController {
    private final AutenticacaoService service; private final UsuarioAtualService currentUser;
    @PostMapping("/login") public RespostaLogin login(@Valid @RequestBody SolicitacaoLogin request) { return service.login(request); }
    @GetMapping("/me") public VisaoUsuario me() { return service.view(currentUser.get()); }
}
