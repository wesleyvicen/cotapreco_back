package br.com.cotapreco.controller;

import br.com.cotapreco.dto.RepresentanteDtos.*;
import br.com.cotapreco.security.RepresentanteAtualService;
import br.com.cotapreco.service.AutenticacaoRepresentanteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;

@RestController
@RequestMapping("/api/publico/representantes")
@RequiredArgsConstructor
public class RepresentanteController {
    private final AutenticacaoRepresentanteService servico;
    private final RepresentanteAtualService representanteAtual;
    @Value("${app.refresh-token.expiration-days:90}") private long refreshExpirationDays;
    @Value("${app.refresh-token.cookie-secure:false}") private boolean refreshCookieSecure;

    @PostMapping("/cadastro") public ResponseEntity<RespostaAutenticacaoRepresentante> cadastrar(@Valid @RequestBody SolicitacaoCadastroRepresentante solicitacao) { return resposta(servico.cadastrar(solicitacao)); }
    @PostMapping("/login") public ResponseEntity<RespostaAutenticacaoRepresentante> entrar(@Valid @RequestBody SolicitacaoLoginRepresentante solicitacao) { return resposta(servico.entrar(solicitacao)); }
    @PostMapping("/refresh") public ResponseEntity<RespostaAutenticacaoRepresentante> refresh(@CookieValue(name="cotapreco_refresh_representante",required=false) String refreshToken,@RequestHeader(name="X-Requested-With",required=false) String solicitadoPor) { validarSolicitacao(solicitadoPor);return resposta(servico.renovar(refreshToken)); }
    @PostMapping("/logout") public ResponseEntity<Void> logout(@CookieValue(name="cotapreco_refresh_representante",required=false) String refreshToken,@RequestHeader(name="X-Requested-With",required=false) String solicitadoPor) { validarSolicitacao(solicitadoPor);servico.sair(refreshToken);return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,cookie("").maxAge(Duration.ZERO).build().toString()).build(); }
    @PostMapping("/esqueci-senha") public MensagemRepresentante esqueciSenha(@Valid @RequestBody SolicitacaoEsqueciSenha solicitacao) { return servico.solicitarRedefinicao(solicitacao); }
    @PostMapping("/redefinir-senha") public MensagemRepresentante redefinirSenha(@Valid @RequestBody SolicitacaoRedefinicaoSenha solicitacao) { return servico.redefinirSenha(solicitacao); }
    @PutMapping("/senha") public MensagemRepresentante alterarSenha(@Valid @RequestBody SolicitacaoAlteracaoSenhaRepresentante solicitacao) { return servico.alterarSenha(representanteAtual.obter(), solicitacao); }
    @GetMapping("/eu") public VisaoRepresentante eu() { return servico.visualizar(representanteAtual.obter()); }
    private ResponseEntity<RespostaAutenticacaoRepresentante> resposta(AutenticacaoRepresentanteService.ResultadoAutenticacao resultado) { return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie(resultado.refreshToken(),Duration.between(java.time.Instant.now(),resultado.refreshExpiresAt())).build().toString()).body(resultado.resposta()); }
    private ResponseCookie.ResponseCookieBuilder cookie(String valor) { return cookie(valor,Duration.ofDays(refreshExpirationDays)); }
    private ResponseCookie.ResponseCookieBuilder cookie(String valor, Duration validade) { return ResponseCookie.from("cotapreco_refresh_representante",valor).httpOnly(true).secure(refreshCookieSecure).sameSite("Lax").path("/api/publico/representantes").maxAge(validade.isNegative()?Duration.ZERO:validade); }
    private void validarSolicitacao(String valor) { if (!"XMLHttpRequest".equals(valor)) throw new br.com.cotapreco.exception.CredencialInvalidaException(); }
}
