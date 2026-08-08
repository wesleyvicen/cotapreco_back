package br.com.cotapreco.controller;

import br.com.cotapreco.dto.AutenticacaoDtos.*;
import br.com.cotapreco.security.UsuarioAtualService;
import br.com.cotapreco.service.AutenticacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AutenticacaoController {
    private final AutenticacaoService service; private final UsuarioAtualService currentUser;
    @Value("${app.refresh-token.expiration-days:90}") private long refreshExpirationDays;
    @Value("${app.refresh-token.cookie-secure:false}") private boolean refreshCookieSecure;
    @PostMapping("/login") public ResponseEntity<RespostaLogin> login(@Valid @RequestBody SolicitacaoLogin request) { return resposta(service.login(request)); }
    @PostMapping("/register") public ResponseEntity<RespostaLogin> cadastrarFarmacia(@Valid @RequestBody SolicitacaoCadastroFarmacia request) { return resposta(service.cadastrarFarmacia(request)); }
    @PostMapping("/refresh") public ResponseEntity<RespostaLogin> refresh(@CookieValue(name="cotapreco_refresh_farmacia",required=false) String refreshToken,@RequestHeader(name="X-Requested-With",required=false) String solicitadoPor) { validarSolicitacao(solicitadoPor);return resposta(service.renovar(refreshToken)); }
    @PostMapping("/logout") public ResponseEntity<Void> logout(@CookieValue(name="cotapreco_refresh_farmacia",required=false) String refreshToken,@RequestHeader(name="X-Requested-With",required=false) String solicitadoPor) { validarSolicitacao(solicitadoPor);service.sair(refreshToken);return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,cookie("").maxAge(Duration.ZERO).build().toString()).build(); }
    @PostMapping("/esqueci-senha") public RespostaMensagem esqueciSenha(@Valid @RequestBody SolicitacaoEsqueciSenha request) { return service.solicitarRedefinicao(request); }
    @PostMapping("/redefinir-senha") public RespostaMensagem redefinirSenha(@Valid @RequestBody SolicitacaoRedefinicaoSenha request) { return service.redefinirSenha(request); }
    @GetMapping("/me") public VisaoUsuario me() { return service.view(currentUser.get()); }
    @PutMapping("/password") public ResponseEntity<Void> alterarSenha(@Valid @RequestBody SolicitacaoAlteracaoSenha request) {
        service.alterarSenha(currentUser.get(), request);
        return ResponseEntity.noContent().build();
    }
    private ResponseEntity<RespostaLogin> resposta(AutenticacaoService.ResultadoLogin resultado) { return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie(resultado.refreshToken(),Duration.between(java.time.Instant.now(),resultado.refreshExpiresAt())).build().toString()).body(resultado.resposta()); }
    private ResponseCookie.ResponseCookieBuilder cookie(String valor) { return cookie(valor,Duration.ofDays(refreshExpirationDays)); }
    private ResponseCookie.ResponseCookieBuilder cookie(String valor, Duration validade) { return ResponseCookie.from("cotapreco_refresh_farmacia",valor).httpOnly(true).secure(refreshCookieSecure).sameSite("Lax").path("/api/auth").maxAge(validade.isNegative()?Duration.ZERO:validade); }
    private void validarSolicitacao(String valor) { if (!"XMLHttpRequest".equals(valor)) throw new br.com.cotapreco.exception.CredencialInvalidaException(); }
}
