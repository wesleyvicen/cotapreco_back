package br.com.cotapreco.service;

import br.com.cotapreco.model.ItemPedidoCompra;
import br.com.cotapreco.model.PedidoCompra;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class GeradorImagemPedidoService {
    private static final int LARGURA = 1200;
    private static final int MARGEM = 64;
    private static final Color VERDE = new Color(14, 77, 59);
    private static final Color VERDE_CLARO = new Color(230, 242, 237);
    private static final Color TEXTO = new Color(24, 51, 43);
    private static final Color CINZA = new Color(104, 125, 117);
    private static final Color LINHA = new Color(220, 231, 226);

    public byte[] gerar(PedidoCompra pedido) {
        try {
            int altura = 510 + Math.max(1, pedido.getItens().size()) * 82;
            BufferedImage imagem = new BufferedImage(LARGURA, altura, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = imagem.createGraphics();
            configurar(g);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, LARGURA, altura);

            g.setColor(VERDE);
            g.fillRoundRect(36, 34, LARGURA - 72, 124, 24, 24);
            escrever(g, "PEDIDO DE COMPRA", MARGEM, 88, 31, Font.BOLD, Color.WHITE);
            escrever(g, "Pedido " + pedido.getNumero() + "  |  Cotação: " + pedido.getCotacao().getNome(), MARGEM, 126, 17, Font.PLAIN, new Color(210, 232, 224));

            int y = 198;
            desenharParte(g, MARGEM, y, 514, "COMPRADOR", pedido.getNomeFarmacia(), "CNPJ: " + formatarCnpj(pedido.getCnpjFarmacia()));
            String cnpjDistribuidora = pedido.getCnpjDistribuidora() == null ? "Não informado" : formatarCnpj(pedido.getCnpjDistribuidora());
            desenharParte(g, 622, y, 514, "DISTRIBUIDORA", pedido.getNomeDistribuidora(), "CNPJ: " + cnpjDistribuidora);
            escrever(g, "Representante: " + pedido.getNomeRepresentante() + "  |  " + pedido.getTelefoneRepresentante(), 644, y + 104, 14, Font.PLAIN, CINZA);

            y = 348;
            g.setColor(VERDE_CLARO);
            g.fillRoundRect(MARGEM, y, LARGURA - MARGEM * 2, 48, 12, 12);
            cabecalho(g, "EAN", 82, y + 30);
            cabecalho(g, "PRODUTO", 270, y + 30);
            cabecalho(g, "QTD.", 754, y + 30);
            cabecalho(g, "UNITÁRIO", 850, y + 30);
            cabecalho(g, "SUBTOTAL", 1022, y + 30);
            y += 48;

            for (ItemPedidoCompra item : pedido.getItens()) {
                g.setColor(LINHA);
                g.drawLine(MARGEM, y + 81, LARGURA - MARGEM, y + 81);
                escrever(g, item.getEan() == null ? "—" : item.getEan(), 82, y + 43, 15, Font.PLAIN, CINZA);
                List<String> produto = quebrar(g, item.getProduto(), 440, new Font(Font.SANS_SERIF, Font.BOLD, 16));
                escrever(g, produto.get(0), 270, y + 33, 16, Font.BOLD, TEXTO);
                if (produto.size() > 1) escrever(g, produto.get(1), 270, y + 57, 16, Font.BOLD, TEXTO);
                escrever(g, String.valueOf(item.getQuantidade()), 754, y + 43, 16, Font.BOLD, TEXTO);
                escrever(g, moeda(item.getPrecoUnitario()), 850, y + 43, 16, Font.PLAIN, TEXTO);
                escrever(g, moeda(item.getSubtotal()), 1022, y + 43, 16, Font.BOLD, TEXTO);
                y += 82;
            }

            y += 28;
            escrever(g, "TOTAL DO PEDIDO", 720, y, 17, Font.BOLD, CINZA);
            escreverDireita(g, moeda(pedido.getTotal()), LARGURA - MARGEM, y + 2, 28, Font.BOLD, VERDE);
            String data = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Recife")).format(pedido.getGeradoEm());
            escrever(g, "Gerado pelo CotaPreço em " + data, MARGEM, altura - 44, 13, Font.PLAIN, CINZA);

            g.dispose();
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            ImageIO.write(imagem, "png", saida);
            return saida.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Não foi possível gerar a imagem do pedido.", ex);
        }
    }

    private void configurar(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    }

    private void desenharParte(Graphics2D g, int x, int y, int largura, String titulo, String nome, String documento) {
        g.setColor(new Color(248, 251, 250));
        g.fillRoundRect(x, y, largura, 126, 18, 18);
        g.setColor(LINHA);
        g.drawRoundRect(x, y, largura, 126, 18, 18);
        escrever(g, titulo, x + 22, y + 30, 13, Font.BOLD, VERDE);
        escrever(g, nome, x + 22, y + 62, 19, Font.BOLD, TEXTO);
        escrever(g, documento, x + 22, y + 91, 14, Font.PLAIN, CINZA);
    }

    private void cabecalho(Graphics2D g, String texto, int x, int y) {
        escrever(g, texto, x, y, 13, Font.BOLD, VERDE);
    }

    private void escrever(Graphics2D g, String texto, int x, int y, int tamanho, int estilo, Color cor) {
        g.setFont(new Font(Font.SANS_SERIF, estilo, tamanho));
        g.setColor(cor);
        g.drawString(texto == null ? "" : texto, x, y);
    }

    private void escreverDireita(Graphics2D g, String texto, int x, int y, int tamanho, int estilo, Color cor) {
        g.setFont(new Font(Font.SANS_SERIF, estilo, tamanho));
        g.setColor(cor);
        g.drawString(texto, x - g.getFontMetrics().stringWidth(texto), y);
    }

    private List<String> quebrar(Graphics2D g, String texto, int largura, Font fonte) {
        g.setFont(fonte);
        List<String> linhas = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        for (String palavra : texto.split("\\s+")) {
            String candidata = atual.isEmpty() ? palavra : atual + " " + palavra;
            if (g.getFontMetrics().stringWidth(candidata) <= largura || atual.isEmpty()) atual = new StringBuilder(candidata);
            else { linhas.add(atual.toString()); atual = new StringBuilder(palavra); }
        }
        if (!atual.isEmpty()) linhas.add(atual.toString());
        if (linhas.isEmpty()) linhas.add("");
        if (linhas.size() > 2) linhas.set(1, linhas.get(1) + "…");
        return linhas.subList(0, Math.min(2, linhas.size()));
    }

    private String moeda(BigDecimal valor) {
        return "R$ " + valor.setScale(2, RoundingMode.HALF_UP).toString().replace('.', ',');
    }

    private String formatarCnpj(String valor) {
        return valor.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }
}
