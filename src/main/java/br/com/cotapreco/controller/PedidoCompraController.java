package br.com.cotapreco.controller;
import br.com.cotapreco.dto.PedidoCompraDtos.*;
import br.com.cotapreco.service.PedidoCompraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController @RequestMapping("/api/quotations/{quotationId}") @RequiredArgsConstructor
public class PedidoCompraController {
    private final PedidoCompraService service;
    @GetMapping("/orders") public List<VisaoPedido> listar(@PathVariable Long quotationId){return service.listar(quotationId);}
    @PutMapping("/orders/{responseId}") @PreAuthorize("hasAnyRole('ADMIN','BUYER')") public VisaoPedido gerar(@PathVariable Long quotationId,@PathVariable Long responseId,@Valid @RequestBody SolicitacaoGeracaoPedido request){return service.gerar(quotationId,responseId,request);}
    @GetMapping("/orders/{orderId}/pdf") public ResponseEntity<byte[]> pdf(@PathVariable Long quotationId,@PathVariable Long orderId){byte[] arquivo=service.pdf(quotationId,orderId);return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,disposicaoArquivo(service.nomeArquivo(quotationId,orderId,"pdf"))).body(arquivo);}
    @GetMapping("/orders/{orderId}/image") public ResponseEntity<byte[]> imagem(@PathVariable Long quotationId,@PathVariable Long orderId){byte[] arquivo=service.imagem(quotationId,orderId);return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).header(HttpHeaders.CONTENT_DISPOSITION,disposicaoArquivo(service.nomeArquivo(quotationId,orderId,"png"))).body(arquivo);}
    @PostMapping("/orders/{orderId}/shared") @PreAuthorize("hasAnyRole('ADMIN','BUYER')") public VisaoPedido compartilhar(@PathVariable Long quotationId,@PathVariable Long orderId){return service.compartilhar(quotationId,orderId);}
    @PostMapping("/complete") @PreAuthorize("hasAnyRole('ADMIN','BUYER')") public ResponseEntity<Void> finalizar(@PathVariable Long quotationId,@Valid @RequestBody SolicitacaoFinalizacao request){service.finalizar(quotationId,request);return ResponseEntity.noContent().build();}
    private String disposicaoArquivo(String nome){return ContentDisposition.attachment().filename(nome,StandardCharsets.UTF_8).build().toString();}
}
