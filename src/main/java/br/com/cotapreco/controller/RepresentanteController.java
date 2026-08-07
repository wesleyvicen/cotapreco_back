package br.com.cotapreco.controller;

import br.com.cotapreco.dto.RepresentanteDtos.*;
import br.com.cotapreco.security.RepresentanteAtualService;
import br.com.cotapreco.service.AutenticacaoRepresentanteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/publico/representantes")
@RequiredArgsConstructor
public class RepresentanteController {
    private final AutenticacaoRepresentanteService servico;
    private final RepresentanteAtualService representanteAtual;

    @PostMapping("/cadastro") public RespostaAutenticacaoRepresentante cadastrar(@Valid @RequestBody SolicitacaoCadastroRepresentante solicitacao) { return servico.cadastrar(solicitacao); }
    @PostMapping("/login") public RespostaAutenticacaoRepresentante entrar(@Valid @RequestBody SolicitacaoLoginRepresentante solicitacao) { return servico.entrar(solicitacao); }
    @PostMapping("/esqueci-senha") public MensagemRepresentante esqueciSenha(@Valid @RequestBody SolicitacaoEsqueciSenha solicitacao) { return servico.solicitarRedefinicao(solicitacao); }
    @PostMapping("/redefinir-senha") public MensagemRepresentante redefinirSenha(@Valid @RequestBody SolicitacaoRedefinicaoSenha solicitacao) { return servico.redefinirSenha(solicitacao); }
    @PutMapping("/senha") public MensagemRepresentante alterarSenha(@Valid @RequestBody SolicitacaoAlteracaoSenhaRepresentante solicitacao) { return servico.alterarSenha(representanteAtual.obter(), solicitacao); }
    @GetMapping("/eu") public VisaoRepresentante eu() { return servico.visualizar(representanteAtual.obter()); }
}
