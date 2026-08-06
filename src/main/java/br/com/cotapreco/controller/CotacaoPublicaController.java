package br.com.cotapreco.controller;

import br.com.cotapreco.dto.CotacaoPublicaDtos.*;
import br.com.cotapreco.service.CotacaoPublicaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/public") @RequiredArgsConstructor
public class CotacaoPublicaController {
    private final CotacaoPublicaService service;
    @GetMapping("/quotations/{token}") public VisaoCotacaoPublica quotation(@PathVariable String token) { return service.getCotacao(token); }
    @PostMapping("/quotations/{token}/responses") public InicioResposta start(@PathVariable String token, @Valid @RequestBody SolicitacaoInicioResposta request) { return service.start(token, request); }
    @GetMapping("/responses/{responseToken}") public VisaoRespostaPublica response(@PathVariable String responseToken) { return service.getResponse(responseToken); }
    @PutMapping("/responses/{responseToken}/items") public VisaoRespostaPublica update(@PathVariable String responseToken, @Valid @RequestBody SolicitacaoAtualizacaoItens request) { return service.updateItems(responseToken, request); }
    @PostMapping("/responses/{responseToken}/submit") public VisaoRespostaPublica submit(@PathVariable String responseToken) { return service.submit(responseToken); }
}
