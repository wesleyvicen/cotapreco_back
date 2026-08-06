package br.com.cotapreco.service;
import br.com.cotapreco.model.*;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.springframework.stereotype.Service;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Service
public class GeradorPdfPedidoService {
    public byte[] gerar(PedidoCompra pedido){
        try{ByteArrayOutputStream saida=new ByteArrayOutputStream();Document documento=new Document(PageSize.A4,36,36,42,42);PdfWriter.getInstance(documento,saida);documento.open();
            Font titulo=FontFactory.getFont(FontFactory.HELVETICA_BOLD,18,new Color(14,77,59));Font forte=FontFactory.getFont(FontFactory.HELVETICA_BOLD,10);Font normal=FontFactory.getFont(FontFactory.HELVETICA,9);
            documento.add(new Paragraph("PEDIDO DE COMPRA",titulo));documento.add(new Paragraph("Pedido: "+pedido.getNumero()+"   |   Cotação: "+pedido.getCotacao().getNome(),normal));documento.add(Chunk.NEWLINE);
            PdfPTable partes=new PdfPTable(2);partes.setWidthPercentage(100);partes.setWidths(new float[]{1,1});
            partes.addCell(celula("COMPRADOR\n"+pedido.getNomeFarmacia()+"\nCNPJ: "+formatarCnpj(pedido.getCnpjFarmacia()),forte,normal));
            partes.addCell(celula("DISTRIBUIDORA\n"+pedido.getNomeDistribuidora()+"\nCNPJ: "+(pedido.getCnpjDistribuidora()==null?"Não informado":formatarCnpj(pedido.getCnpjDistribuidora()))+"\nRepresentante: "+pedido.getNomeRepresentante()+" - "+pedido.getTelefoneRepresentante(),forte,normal));
            documento.add(partes);documento.add(Chunk.NEWLINE);
            PdfPTable tabela=new PdfPTable(5);tabela.setWidthPercentage(100);tabela.setWidths(new float[]{1.35f,3.8f,.7f,1.1f,1.2f});
            for(String cab:new String[]{"EAN","Produto","Qtd.","Unitário","Subtotal"})tabela.addCell(cabecalho(cab,forte));
            tabela.setHeaderRows(1);
            for(ItemPedidoCompra item:pedido.getItens()){
                tabela.addCell(corpo(item.getEan()==null?"—":item.getEan(),normal));tabela.addCell(corpo(item.getProduto(),normal));tabela.addCell(corpo(String.valueOf(item.getQuantidade()),normal));
                tabela.addCell(corpo(moeda(item.getPrecoUnitario()),normal));tabela.addCell(corpo(moeda(item.getSubtotal()),normal));
            }
            documento.add(tabela);Paragraph total=new Paragraph("TOTAL DO PEDIDO: "+moeda(pedido.getTotal()),FontFactory.getFont(FontFactory.HELVETICA_BOLD,14,new Color(14,77,59)));total.setAlignment(Element.ALIGN_RIGHT);total.setSpacingBefore(12);documento.add(total);
            documento.add(Chunk.NEWLINE);String data=DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Recife")).format(pedido.getGeradoEm());documento.add(new Paragraph("Gerado pelo CotaPreço em "+data,FontFactory.getFont(FontFactory.HELVETICA,8,Color.GRAY)));documento.close();return saida.toByteArray();
        }catch(Exception ex){throw new IllegalStateException("Não foi possível gerar o PDF do pedido.",ex);}
    }
    private PdfPCell celula(String texto,Font forte,Font normal){String[] partes=texto.split("\\n",2);PdfPCell c=new PdfPCell();c.setPadding(10);c.setBorderColor(new Color(210,225,219));c.addElement(new Paragraph(partes[0],forte));if(partes.length>1)c.addElement(new Paragraph(partes[1],normal));return c;}
    private PdfPCell cabecalho(String t,Font f){PdfPCell c=new PdfPCell(new Phrase(t,f));c.setPadding(6);c.setBackgroundColor(new Color(230,242,237));return c;}
    private PdfPCell corpo(String t,Font f){PdfPCell c=new PdfPCell(new Phrase(t,f));c.setPadding(5);c.setBorderColor(new Color(225,232,229));return c;}
    private String moeda(java.math.BigDecimal v){return "R$ "+v.setScale(2,RoundingMode.HALF_UP).toString().replace('.',',');}
    private String formatarCnpj(String v){return v.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})","$1.$2.$3/$4-$5");}
}
