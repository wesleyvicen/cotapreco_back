package br.com.cotapreco.controller;

import br.com.cotapreco.dto.AutenticacaoDtos.*;
import br.com.cotapreco.security.UsuarioAtualService;
import br.com.cotapreco.service.AutenticacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AutenticacaoController {
    private final AutenticacaoService service; private final UsuarioAtualService currentUser;
    @PostMapping("/login") public RespostaLogin login(@Valid @RequestBody SolicitacaoLogin request) { return service.login(request); }
    @PostMapping("/register") public RespostaLogin cadastrarFarmacia(@Valid @RequestBody SolicitacaoCadastroFarmacia request) { return service.cadastrarFarmacia(request); }
    @PostMapping("/esqueci-senha") public RespostaMensagem esqueciSenha(@Valid @RequestBody SolicitacaoEsqueciSenha request) { return service.solicitarRedefinicao(request); }
    @PostMapping("/redefinir-senha") public RespostaMensagem redefinirSenha(@Valid @RequestBody SolicitacaoRedefinicaoSenha request) { return service.redefinirSenha(request); }
    @GetMapping("/me") public VisaoUsuario me() { return service.view(currentUser.get()); }
    @PutMapping("/password") public ResponseEntity<Void> alterarSenha(@Valid @RequestBody SolicitacaoAlteracaoSenha request) {
        service.alterarSenha(currentUser.get(), request);
        return ResponseEntity.noContent().build();
    }
}
