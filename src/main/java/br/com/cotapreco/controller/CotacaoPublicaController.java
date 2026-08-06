package br.com.cotapreco.controller;

import br.com.cotapreco.dto.CotacaoPublicaDtos.*;
import br.com.cotapreco.security.RepresentanteAtualService;
import br.com.cotapreco.service.CotacaoPublicaService;
import br.com.cotapreco.service.CompartilhamentoCotacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/publico/cotacoes")
@RequiredArgsConstructor
public class CotacaoPublicaController {
    private final CotacaoPublicaService servico;
    private final CompartilhamentoCotacaoService compartilhamento;
    private final RepresentanteAtualService representanteAtual;

    @GetMapping("/{token}") public VisaoCotacaoPublica cotacao(@PathVariable String token) { return servico.obterCotacao(token); }
    @GetMapping(value="/{token}/compartilhar",produces=MediaType.TEXT_HTML_VALUE) public ResponseEntity<String> compartilhar(@PathVariable String token) { return ResponseEntity.ok().cacheControl(CacheControl.noCache()).contentType(MediaType.parseMediaType("text/html;charset=UTF-8")).body(compartilhamento.pagina(token)); }
    @GetMapping(value="/{token}/imagem-compartilhamento",produces=MediaType.IMAGE_JPEG_VALUE) public ResponseEntity<byte[]> imagemCompartilhamento(@PathVariable String token) { return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic()).contentType(MediaType.IMAGE_JPEG).body(compartilhamento.imagem(token)); }
    @GetMapping("/{token}/minhas-respostas") public List<ResumoRespostaPublica> minhasRespostas(@PathVariable String token) { return servico.listarMinhasRespostas(token, representanteAtual.obter()); }
    @PostMapping("/{token}/respostas") public VisaoRespostaPublica criar(@PathVariable String token, @Valid @RequestBody SolicitacaoNovaResposta solicitacao) { return servico.criarResposta(token, solicitacao, representanteAtual.obter()); }
    @GetMapping("/{token}/respostas/{id}") public VisaoRespostaPublica resposta(@PathVariable String token, @PathVariable Long id) { return servico.obterResposta(token, id, representanteAtual.obter()); }
    @PutMapping("/{token}/respostas/{id}") public VisaoRespostaPublica atualizar(@PathVariable String token, @PathVariable Long id, @Valid @RequestBody SolicitacaoAtualizacaoResposta solicitacao) { return servico.atualizarResposta(token, id, solicitacao, representanteAtual.obter()); }
    @PostMapping("/{token}/respostas/{id}/enviar") public VisaoRespostaPublica enviar(@PathVariable String token, @PathVariable Long id) { return servico.enviarResposta(token, id, representanteAtual.obter()); }
}
