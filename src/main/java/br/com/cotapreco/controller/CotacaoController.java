package br.com.cotapreco.controller;

import br.com.cotapreco.dto.ComparacaoDtos.VisaoComparacao;
import br.com.cotapreco.dto.CotacaoDtos.*;
import br.com.cotapreco.dto.EscolhaCompraDtos.*;
import br.com.cotapreco.security.UsuarioAtualService;
import br.com.cotapreco.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController @RequestMapping("/api/quotations") @RequiredArgsConstructor
public class CotacaoController {
    private final CotacaoService service; private final ComparacaoCotacaoService comparisonService;
    private final EscolhaCompraCotacaoService purchaseSelectionService; private final PlanoCompraService purchasePlanService; private final UsuarioAtualService currentUser;
    @GetMapping public List<ResumoCotacao> list() { return service.list(); }
    @GetMapping("/{id}") public VisaoCotacao get(@PathVariable Long id) { return service.get(id); }
    @PostMapping @PreAuthorize("hasAnyRole('ADMIN','BUYER')") public VisaoCotacao create(@Valid @RequestBody SolicitacaoCriacaoCotacao request) { return service.create(request); }
    @PostMapping(value = "/import/preview", consumes = "multipart/form-data") @PreAuthorize("hasAnyRole('ADMIN','BUYER')") public PreviaImportacao preview(@RequestPart("file") MultipartFile file) { return service.preview(file); }
    @PostMapping("/{id}/open") @PreAuthorize("hasAnyRole('ADMIN','BUYER')") public VisaoCotacao open(@PathVariable Long id) { return service.open(id); }
    @PostMapping("/{id}/close") @PreAuthorize("hasAnyRole('ADMIN','BUYER')") public VisaoCotacao close(@PathVariable Long id) { return service.close(id); }
    @GetMapping("/{id}/responses") public List<VisaoResposta> responses(@PathVariable Long id) { return service.responses(id); }
    @GetMapping("/{id}/comparison") public VisaoComparacao comparison(@PathVariable Long id) { return comparisonService.compare(id, currentUser.companyId()); }
    @PutMapping("/{id}/purchase-selections/{itemId}") @PreAuthorize("hasAnyRole('ADMIN','BUYER')")
    public VisaoEscolhaCompra choosePurchase(@PathVariable Long id, @PathVariable Long itemId, @Valid @RequestBody SolicitacaoEscolhaCompra request) {
        return purchaseSelectionService.escolher(id, itemId, request);
    }
    @DeleteMapping("/{id}/purchase-selections/{itemId}") @PreAuthorize("hasAnyRole('ADMIN','BUYER')")
    public void resetPurchase(@PathVariable Long id, @PathVariable Long itemId) { purchaseSelectionService.voltarAoAutomatico(id, itemId); }
    @PutMapping("/{id}/purchase-plan") @PreAuthorize("hasAnyRole('ADMIN','BUYER')")
    public VisaoComparacao updatePurchasePlan(@PathVariable Long id,@Valid @RequestBody br.com.cotapreco.dto.PlanoCompraDtos.SolicitacaoPlanoCompra request){return purchasePlanService.atualizar(id,request);}
}
