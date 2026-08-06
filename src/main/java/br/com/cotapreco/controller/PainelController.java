package br.com.cotapreco.controller;

import br.com.cotapreco.dto.CotacaoDtos.VisaoPainel;
import br.com.cotapreco.service.CotacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/dashboard") @RequiredArgsConstructor
public class PainelController {
    private final CotacaoService service;
    @GetMapping public VisaoPainel dashboard() { return service.dashboard(); }
}
