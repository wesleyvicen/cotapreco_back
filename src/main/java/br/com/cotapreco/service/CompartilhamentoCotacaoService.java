package br.com.cotapreco.service;

import br.com.cotapreco.exception.RecursoNaoEncontradoException;
import br.com.cotapreco.model.Cotacao;
import br.com.cotapreco.repository.CotacaoRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompartilhamentoCotacaoService {
    private static final int LARGURA = 1200;
    private static final int ALTURA = 630;
    private static final Color BRANCO = new Color(248, 252, 250);
    private static final Color DOURADO = new Color(240, 202, 107);
    private static final Color MENTA = new Color(190, 222, 211);
    private final CotacaoRepository cotacoes;
    @Value("${app.frontend-url}") private String urlFrontend;
    @Value("${app.share-public-url:${app.backend-public-url}}") private String urlPublicaCompartilhamento;
    private BufferedImage imagemBase;

    @PostConstruct
    void carregarImagemBase() {
        try {
            imagemBase = ImageIO.read(new ClassPathResource("social/cotapreco-compartilhamento-base.jpg").getInputStream());
        } catch (Exception ex) {
            throw new IllegalStateException("Não foi possível carregar a imagem de compartilhamento.", ex);
        }
    }

    @Transactional(readOnly = true)
    public String pagina(String token) {
        Cotacao cotacao = buscar(token);
        String farmacia = cotacao.getEmpresa().getNome();
        String titulo = "Cotação: " + cotacao.getNome();
        String descricao = farmacia + " está solicitando preços para " + cotacao.getItens().size() + " produtos. Acesse o link para responder.";
        String destino = frontend() + "/cotacao/responder/" + token;
        String versao = cotacao.getExpiraEm() == null ? "" : "?v=" + cotacao.getExpiraEm().toEpochMilli();
        String pagina = backend() + "/api/publico/cotacoes/" + token + "/compartilhar" + versao;
        String imagem = backend() + "/api/publico/cotacoes/" + token + "/imagem-compartilhamento" + versao;
        return """
            <!doctype html><html lang="pt-BR"><head><meta charset="UTF-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>%s — %s</title><meta name="description" content="%s">
            <meta name="robots" content="noindex,nofollow">
            <meta property="og:type" content="website"><meta property="og:site_name" content="CotaPreço">
            <meta property="og:locale" content="pt_BR"><meta property="og:url" content="%s">
            <meta property="og:title" content="%s"><meta property="og:description" content="%s">
            <meta property="og:image" content="%s"><meta property="og:image:secure_url" content="%s">
            <meta property="og:image:type" content="image/jpeg"><meta property="og:image:width" content="1200">
            <meta property="og:image:height" content="630"><meta property="og:image:alt" content="Convite de cotação de %s">
            <meta name="twitter:card" content="summary_large_image"><meta name="twitter:title" content="%s">
            <meta name="twitter:description" content="%s"><meta name="twitter:image" content="%s">
            <link rel="canonical" href="%s"><meta http-equiv="refresh" content="0;url=%s">
            <style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#0e4d3b;color:#fff;font:16px system-ui;text-align:center}a{color:#f0ca6b;font-weight:700}</style>
            </head><body><main><p>Abrindo a cotação de <strong>%s</strong>…</p><a href="%s">Clique aqui se não for redirecionado</a></main></body></html>
            """.formatted(esc(titulo), esc(farmacia), esc(descricao), esc(pagina), esc(titulo), esc(descricao), esc(imagem), esc(imagem), esc(farmacia), esc(titulo), esc(descricao), esc(imagem), esc(destino), esc(destino), esc(farmacia), esc(destino));
    }

    @Transactional(readOnly = true)
    public byte[] imagem(String token) {
        Cotacao cotacao = buscar(token);
        BufferedImage imagem = new BufferedImage(LARGURA, ALTURA, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawImage(imagemBase, 0, 0, LARGURA, ALTURA, null);
        escrever(g, "COTAPREÇO  •  CONVITE PARA COTAÇÃO", 70, 92, 19, Font.BOLD, DOURADO);
        escrever(g, cotacao.getEmpresa().getNome(), 70, 148, 28, Font.BOLD, MENTA);
        int y = 222;
        for (String linha : quebrar(g, cotacao.getNome(), 610, new Font(Font.SANS_SERIF, Font.BOLD, 50), 3)) {
            escrever(g, linha, 70, y, 50, Font.BOLD, BRANCO);
            y += 62;
        }
        y += 20;
        escrever(g, cotacao.getItens().size() + (cotacao.getItens().size() == 1 ? " produto para cotar" : " produtos para cotar"), 70, y, 24, Font.PLAIN, MENTA);
        if (cotacao.getExpiraEm() != null) {
            String prazo = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.of("America/Recife")).format(cotacao.getExpiraEm());
            escrever(g, "Prazo: " + prazo, 70, y + 40, 20, Font.PLAIN, MENTA);
        }
        g.dispose();
        try {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            ImageIO.write(imagem, "jpg", saida);
            return saida.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Não foi possível gerar a imagem de compartilhamento.", ex);
        }
    }

    private Cotacao buscar(String token) {
        return cotacoes.findByTokenPublico(token).orElseThrow(() -> new RecursoNaoEncontradoException("Cotação não encontrada."));
    }
    private String frontend() { return removerBarra(urlFrontend.split(",")[0].trim()); }
    private String backend() { return removerBarra(urlPublicaCompartilhamento); }
    private String removerBarra(String valor) { return valor.endsWith("/") ? valor.substring(0, valor.length() - 1) : valor; }
    private String esc(String valor) { return HtmlUtils.htmlEscape(valor); }
    private void escrever(Graphics2D g, String texto, int x, int y, int tamanho, int estilo, Color cor) { g.setFont(new Font(Font.SANS_SERIF, estilo, tamanho));g.setColor(cor);g.drawString(texto, x, y); }
    private List<String> quebrar(Graphics2D g, String texto, int largura, Font fonte, int maximo) {
        g.setFont(fonte);List<String> linhas = new ArrayList<>();StringBuilder atual = new StringBuilder();
        for (String palavra : texto.trim().split("\\s+")) {String candidata = atual.isEmpty() ? palavra : atual + " " + palavra;if (g.getFontMetrics().stringWidth(candidata) <= largura || atual.isEmpty()) atual = new StringBuilder(candidata);else {linhas.add(atual.toString());atual = new StringBuilder(palavra);}}
        if (!atual.isEmpty()) linhas.add(atual.toString());if (linhas.size() > maximo) {linhas = new ArrayList<>(linhas.subList(0, maximo));linhas.set(maximo - 1, linhas.get(maximo - 1) + "…");}return linhas;
    }
}
