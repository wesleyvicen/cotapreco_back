package br.com.cotapreco.controller;
import br.com.cotapreco.dto.EmpresaDtos.*;
import br.com.cotapreco.service.EmpresaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/company") @RequiredArgsConstructor
public class EmpresaController {
    private final EmpresaService service;
    @GetMapping public VisaoEmpresa obter(){return service.obter();}
    @PutMapping @PreAuthorize("hasRole('ADMIN')") public VisaoEmpresa atualizar(@Valid @RequestBody SolicitacaoEmpresa solicitacao){return service.atualizar(solicitacao);}
}
