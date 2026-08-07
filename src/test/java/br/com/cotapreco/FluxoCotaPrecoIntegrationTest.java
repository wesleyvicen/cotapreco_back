package br.com.cotapreco;

import br.com.cotapreco.enums.PerfilUsuario;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class FluxoCotaPrecoIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EmpresaRepository empresas;
    @Autowired UsuarioRepository usuarios;
    @Autowired CotacaoRepository cotacoes;
    @Autowired ProdutoRepository produtos;
    @Autowired RepresentanteRepository representantes;
    @Autowired TokenRedefinicaoSenhaRepresentanteRepository tokensRedefinicao;
    @Autowired TokenRedefinicaoSenhaUsuarioRepository tokensRedefinicaoUsuarios;
    @Autowired PasswordEncoder codificador;

    @Test
    void fluxoCompletoComContaVariasDistribuidorasCorrecaoEComparacao() throws Exception {
        String autenticacaoFarmacia = loginFarmacia("admin@cotapreco.local", "Cotapreco@123");
        String tokenCotacao = criarEAbrirCotacao(autenticacaoFarmacia, "Reposição agosto");

        mvc.perform(get("/api/publico/cotacoes/{token}", tokenCotacao)).andExpect(status().isOk())
            .andExpect(jsonPath("$.nomeEmpresa").value("Farmácia Exemplo"))
            .andExpect(jsonPath("$.totalProdutos").value(2))
            .andExpect(jsonPath("$.itens[0].ean").value("7890000000001"))
            .andExpect(jsonPath("$.itens[0].gtin").doesNotExist())
            .andExpect(jsonPath("$.itens[0].id").doesNotExist());
        mvc.perform(get("/api/publico/cotacoes/{token}/compartilhar", tokenCotacao)).andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Cota&ccedil;&atilde;o: Reposi&ccedil;&atilde;o agosto")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Farm&aacute;cia Exemplo")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("og:image")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("http://localhost:5173/cotacao/responder/" + tokenCotacao)));
        byte[] imagemSocial = mvc.perform(get("/api/publico/cotacoes/{token}/imagem-compartilhamento", tokenCotacao))
            .andExpect(status().isOk()).andExpect(content().contentType(MediaType.IMAGE_JPEG))
            .andReturn().getResponse().getContentAsByteArray();
        java.awt.image.BufferedImage previaSocial = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(imagemSocial));
        assertThat(previaSocial.getWidth()).isEqualTo(1200);
        assertThat(previaSocial.getHeight()).isEqualTo(630);

        String autenticacaoRepresentante = cadastrarRepresentante(tokenCotacao, "Ana Souza", "(81) 99999-1001", "ana1001@teste.local");
        mvc.perform(get("/api/publico/representantes/eu").header("Authorization", autenticacaoRepresentante))
            .andExpect(status().isOk()).andExpect(jsonPath("$.telefone").value("81999991001"));
        mvc.perform(get("/api/quotations").header("Authorization", autenticacaoRepresentante)).andExpect(status().isForbidden());

        JsonNode propostaA = criarProposta(tokenCotacao, autenticacaoRepresentante, "Distribuidora A", "12345678000190");
        JsonNode propostaB = criarProposta(tokenCotacao, autenticacaoRepresentante, "Distribuidora B", null);
        long idA = propostaA.get("id").asLong(), idB = propostaB.get("id").asLong();
        long a1 = propostaA.at("/itens/0/id").asLong(), a2 = propostaA.at("/itens/1/id").asLong();
        long b1 = propostaB.at("/itens/0/id").asLong(), b2 = propostaB.at("/itens/1/id").asLong();

        mvc.perform(get("/api/publico/cotacoes/{token}/minhas-respostas", tokenCotacao).header("Authorization", autenticacaoRepresentante))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(post("/api/publico/cotacoes/{token}/respostas", tokenCotacao).header("Authorization", autenticacaoRepresentante)
            .contentType(MediaType.APPLICATION_JSON).content(distribuidoraJson("DISTRIBUIDORA A", "12.345.678/0001-90")))
            .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/publico/cotacoes/{token}/respostas", tokenCotacao)
            .contentType(MediaType.APPLICATION_JSON).content(distribuidoraJson("Sem autenticação", null)))
            .andExpect(status().isUnauthorized());

        mvc.perform(put("/api/publico/cotacoes/{token}/respostas/{id}", tokenCotacao, idB).header("Authorization", autenticacaoRepresentante)
            .contentType(MediaType.APPLICATION_JSON).content(respostaJson("Distribuidora B", null, a1, a2, 9, 60, null, null)))
            .andExpect(status().isUnprocessableEntity());

        atualizarEEnviar(tokenCotacao, idA, autenticacaoRepresentante, respostaJson("Distribuidora A", "12345678000190", a1, a2, 10, 100, 5, 50));
        atualizarEEnviar(tokenCotacao, idB, autenticacaoRepresentante, respostaJson("Distribuidora B", null, b1, b2, 9, 60, null, null));

        mvc.perform(put("/api/publico/cotacoes/{token}/respostas/{id}", tokenCotacao, idA).header("Authorization", autenticacaoRepresentante)
            .contentType(MediaType.APPLICATION_JSON).content(respostaJson("Distribuidora A", "12345678000190", a1, a2, 10, 100, 5, 50)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));

        String outroRepresentante = cadastrarRepresentante(tokenCotacao, "Bruno Lima", "+55 81 99999-1002", "bruno1002@teste.local");
        mvc.perform(get("/api/publico/cotacoes/{token}/respostas/{id}", tokenCotacao, idA).header("Authorization", outroRepresentante))
            .andExpect(status().isNotFound());

        long cotacaoId = localizarCotacaoPorToken(tokenCotacao);
        mvc.perform(get("/api/quotations/{id}", cotacaoId).header("Authorization", autenticacaoFarmacia))
            .andExpect(status().isOk()).andExpect(jsonPath("$.publicUrl").value("http://localhost:8080/api/publico/cotacoes/" + tokenCotacao + "/compartilhar"));
        JsonNode comparacaoAutomatica = json(mvc.perform(get("/api/quotations/{id}/comparison", cotacaoId)
            .header("Authorization", autenticacaoFarmacia)).andExpect(status().isOk())
            .andExpect(jsonPath("$.products[0].winningSupplier").value("Distribuidora B"))
            .andExpect(jsonPath("$.products[0].coveredQuantity").value(100))
            .andExpect(jsonPath("$.bestCompositionTotal").value(1190.0)).andReturn());
        long itemCotacao1 = comparacaoAutomatica.at("/products/0/quotationItemId").asLong();
        long itemCotacao2 = comparacaoAutomatica.at("/products/1/quotationItemId").asLong();

        Usuario administrador = usuarios.findByEmailIgnoreCase("admin@cotapreco.local").orElseThrow();
        Usuario visualizador = new Usuario(); visualizador.setEmpresa(administrador.getEmpresa()); visualizador.setNome("Consulta Compras");
        visualizador.setEmail("viewer-" + UUID.randomUUID() + "@teste.local"); visualizador.setSenhaHash(codificador.encode("Senha@123"));
        visualizador.setPerfil(PerfilUsuario.VIEWER); usuarios.save(visualizador);
        String autenticacaoVisualizador = loginFarmacia(visualizador.getEmail(), "Senha@123");
        mvc.perform(put("/api/quotations/{id}/purchase-selections/{itemId}", cotacaoId, itemCotacao1)
            .header("Authorization", autenticacaoVisualizador).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("responseId", idB))))
            .andExpect(status().isForbidden());

        mvc.perform(put("/api/quotations/{id}/purchase-selections/{itemId}", cotacaoId, itemCotacao1)
            .header("Authorization", autenticacaoFarmacia).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("responseId", idB))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.responseId").value(idB));
        mvc.perform(get("/api/quotations/{id}/comparison", cotacaoId).header("Authorization", autenticacaoFarmacia))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.products[0].manualSelection").value(true))
            .andExpect(jsonPath("$.products[0].selectedResponseId").value(idB))
            .andExpect(jsonPath("$.suggestedPurchase[0].items[0].manualSelection").value(true))
            .andExpect(jsonPath("$.suggestedPurchase[1].items[0].complement").value(true))
            .andExpect(jsonPath("$.bestCompositionTotal").value(1190.0));

        mvc.perform(put("/api/quotations/{id}/purchase-selections/{itemId}", cotacaoId, itemCotacao1)
            .header("Authorization", autenticacaoFarmacia).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("responseId", idA))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/quotations/{id}/comparison", cotacaoId).header("Authorization", autenticacaoFarmacia))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products[0].selectedResponseId").value(idA))
            .andExpect(jsonPath("$.bestCompositionTotal").value(1250.0));
        mvc.perform(get("/api/quotations/{id}/comparison", cotacaoId).header("Authorization", autenticacaoFarmacia))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products[0].selectedResponseId").value(idA));

        mvc.perform(delete("/api/quotations/{id}/purchase-selections/{itemId}", cotacaoId, itemCotacao1)
            .header("Authorization", autenticacaoFarmacia)).andExpect(status().isOk());
        mvc.perform(get("/api/quotations/{id}/comparison", cotacaoId).header("Authorization", autenticacaoFarmacia))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products[0].manualSelection").value(false))
            .andExpect(jsonPath("$.bestCompositionTotal").value(1190.0));

        mvc.perform(put("/api/quotations/{id}/purchase-selections/{itemId}", cotacaoId, itemCotacao1)
            .header("Authorization", autenticacaoFarmacia).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("responseId", idB))))
            .andExpect(status().isOk());
        mvc.perform(put("/api/publico/cotacoes/{token}/respostas/{id}", tokenCotacao, idB).header("Authorization", autenticacaoRepresentante)
            .contentType(MediaType.APPLICATION_JSON).content(respostaJson("Distribuidora B", null, b1, b2, null, null, null, null)))
            .andExpect(status().isOk());
        mvc.perform(get("/api/quotations/{id}/comparison", cotacaoId).header("Authorization", autenticacaoFarmacia))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products[0].invalidManualSelection").value(true))
            .andExpect(jsonPath("$.products[0].manualSelection").value(false))
            .andExpect(jsonPath("$.products[0].winningSupplier").value("Distribuidora A"))
            .andExpect(jsonPath("$.bestCompositionTotal").value(1250.0));

        mvc.perform(post("/api/quotations/{id}/close", cotacaoId).header("Authorization", autenticacaoFarmacia)).andExpect(status().isOk());
        mvc.perform(put("/api/publico/cotacoes/{token}/respostas/{id}", tokenCotacao, idA).header("Authorization", autenticacaoRepresentante)
            .contentType(MediaType.APPLICATION_JSON).content(respostaJson("Distribuidora A", "12345678000190", a1, a2, 8, 100, 5, 50)))
            .andExpect(status().isUnprocessableEntity());

        mvc.perform(put("/api/company").header("Authorization", autenticacaoFarmacia).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("nome", "Farmácia Exemplo", "cnpj", "12345678000195"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.cnpj").value("12345678000195"));
        String planoSemJustificativa = mapper.writeValueAsString(Map.of("items", List.of(
            itemPlano(itemCotacao1, 120, idA, 120, "   ", true), itemPlano(itemCotacao2, 0, null, null, null, false))));
        mvc.perform(put("/api/quotations/{id}/purchase-plan", cotacaoId).header("Authorization", autenticacaoFarmacia)
            .contentType(MediaType.APPLICATION_JSON).content(planoSemJustificativa)).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.fields['itens." + itemCotacao1 + ".stockOverrideNote']").exists());
        String plano120 = mapper.writeValueAsString(Map.of("items", List.of(
            itemPlano(itemCotacao1, 120, idA, 120, "Estoque adicional confirmado por telefone", true),
            itemPlano(itemCotacao2, 0, null, null, null, false))));
        mvc.perform(put("/api/quotations/{id}/purchase-plan", cotacaoId).header("Authorization", autenticacaoFarmacia)
            .contentType(MediaType.APPLICATION_JSON).content(plano120)).andExpect(status().isOk())
            .andExpect(jsonPath("$.products[0].requestedQuantity").value(100))
            .andExpect(jsonPath("$.products[0].desiredQuantity").value(120))
            .andExpect(jsonPath("$.bestCompositionTotal").value(1200.0));

        JsonNode pedido = json(mvc.perform(put("/api/quotations/{id}/orders/{responseId}", cotacaoId, idA)
            .header("Authorization", autenticacaoFarmacia).contentType(MediaType.APPLICATION_JSON)
            .content("{\"observation\":\"Entregar pela manhã\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("GERADO"))
            .andExpect(jsonPath("$.items[0].quantity").value(120)).andReturn());
        long pedidoId = pedido.get("id").asLong();
        byte[] pdf = mvc.perform(get("/api/quotations/{id}/orders/{orderId}/pdf", cotacaoId, pedidoId)
            .header("Authorization", autenticacaoFarmacia)).andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF)).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        com.lowagie.text.pdf.PdfReader leitorPdf = new com.lowagie.text.pdf.PdfReader(pdf);
        String textoPdf = new com.lowagie.text.pdf.parser.PdfTextExtractor(leitorPdf).getTextFromPage(1);
        leitorPdf.close();
        assertThat(textoPdf).doesNotContain("Estoque adicional confirmado por telefone", "Entregar pela manhã", "Observação");
        byte[] imagem = mvc.perform(get("/api/quotations/{id}/orders/{orderId}/image", cotacaoId, pedidoId)
            .header("Authorization", autenticacaoFarmacia)).andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.IMAGE_PNG)).andReturn().getResponse().getContentAsByteArray();
        assertThat(Arrays.copyOf(imagem, 8)).containsExactly((byte) 0x89, (byte) 0x50, (byte) 0x4e, (byte) 0x47, (byte) 0x0d, (byte) 0x0a, (byte) 0x1a, (byte) 0x0a);

        String plano110 = mapper.writeValueAsString(Map.of("items", List.of(
            itemPlano(itemCotacao1, 110, idA, 110, "Nova confirmação de estoque", true),
            itemPlano(itemCotacao2, 0, null, null, null, false))));
        mvc.perform(put("/api/quotations/{id}/purchase-plan", cotacaoId).header("Authorization", autenticacaoFarmacia)
            .contentType(MediaType.APPLICATION_JSON).content(plano110)).andExpect(status().isOk());
        mvc.perform(get("/api/quotations/{id}/orders", cotacaoId).header("Authorization", autenticacaoFarmacia))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].status").value("DESATUALIZADO"));
        mvc.perform(get("/api/quotations/{id}/orders/{orderId}/pdf", cotacaoId, pedidoId).header("Authorization", autenticacaoFarmacia))
            .andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/quotations/{id}/orders/{orderId}/image", cotacaoId, pedidoId).header("Authorization", autenticacaoFarmacia))
            .andExpect(status().isUnprocessableEntity());
        mvc.perform(put("/api/quotations/{id}/orders/{responseId}", cotacaoId, idA).header("Authorization", autenticacaoFarmacia)
            .contentType(MediaType.APPLICATION_JSON).content("{\"observation\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(110));
        mvc.perform(post("/api/quotations/{id}/complete", cotacaoId).header("Authorization", autenticacaoFarmacia)
            .contentType(MediaType.APPLICATION_JSON).content("{\"confirmPartialCoverage\":false}"))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/quotations/{id}/complete", cotacaoId).header("Authorization", autenticacaoFarmacia)
            .contentType(MediaType.APPLICATION_JSON).content("{\"confirmPartialCoverage\":false}"))
            .andExpect(status().isNoContent());
        mvc.perform(get("/api/quotations/{id}", cotacaoId).header("Authorization", autenticacaoFarmacia))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"))
            .andExpect(jsonPath("$.items[0].requestedQuantity").value(100));
    }

    @Test
    void validaTodosOsCamposDaRespostaEExigeObservacaoQuandoQuantidadeExcedePedido() throws Exception {
        String farmacia = loginFarmacia("admin@cotapreco.local", "Cotapreco@123");
        String token = criarEAbrirCotacao(farmacia, "Validação dos itens");
        String representante = cadastrarRepresentante(token, "Eva Campos", "81999995001", "eva5001@teste.local");
        JsonNode proposta = criarProposta(token, representante, "Distribuidora Validação", null);
        long respostaId = proposta.get("id").asLong();
        long item1 = proposta.at("/itens/0/id").asLong();
        long item2 = proposta.at("/itens/1/id").asLong();

        String tresErros = respostaJson("Distribuidora Validação", null,
            itemDisponivel(item1, null, null, null), itemDisponivel(item2, 5, 51, "   "));
        mvc.perform(put("/api/publico/cotacoes/{token}/respostas/{id}", token, respostaId)
            .header("Authorization", representante).contentType(MediaType.APPLICATION_JSON).content(tresErros))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.fields['itens." + item1 + ".precoUnitario']").exists())
            .andExpect(jsonPath("$.fields['itens." + item1 + ".quantidadeDisponivel']").exists())
            .andExpect(jsonPath("$.fields['itens." + item2 + ".observacao']").exists());

        mvc.perform(put("/api/publico/cotacoes/{token}/respostas/{id}", token, respostaId)
            .header("Authorization", representante).contentType(MediaType.APPLICATION_JSON)
            .content(respostaJson("Distribuidora Validação", null, itemDisponivel(item1, 10, 100, null), itemIndisponivel(item2))))
            .andExpect(status().isOk());

        mvc.perform(put("/api/publico/cotacoes/{token}/respostas/{id}", token, respostaId)
            .header("Authorization", representante).contentType(MediaType.APPLICATION_JSON)
            .content(respostaJson("Distribuidora Validação", null, itemDisponivel(item1, 10, 101, "  "), itemIndisponivel(item2))))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.fields['itens." + item1 + ".observacao']").exists());

        mvc.perform(put("/api/publico/cotacoes/{token}/respostas/{id}", token, respostaId)
            .header("Authorization", representante).contentType(MediaType.APPLICATION_JSON)
            .content(respostaJson("Distribuidora Validação", null,
                itemDisponivel(item1, 10, 101, "Venda somente em caixa fechada"), itemIndisponivel(item2))))
            .andExpect(status().isOk());
    }

    @Test
    void eanEOpcionalImportacaoReutilizaNomeNormalizadoEAceitaAliasLegado() throws Exception {
        String farmacia = loginFarmacia("admin@cotapreco.local", "Cotapreco@123");
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        String nome = "Ácido   especial " + sufixo;

        JsonNode semEan = json(mvc.perform(post("/api/products").header("Authorization", farmacia)
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("ean", "", "name", nome))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ean").isEmpty()).andReturn());
        MockMultipartFile semCodigo = new MockMultipartFile("file", "sem-ean.csv", "text/csv",
            ("ean,produto,quantidade\n,acido especial " + sufixo + ",10\n").getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/quotations/import/preview").file(semCodigo).header("Authorization", farmacia))
            .andExpect(status().isOk()).andExpect(jsonPath("$.validRows").value(1))
            .andExpect(jsonPath("$.lines[0].productExists").value(true))
            .andExpect(jsonPath("$.lines[0].productId").value(semEan.get("id").asLong()));

        String eanLegado = "789" + Math.abs(sufixo.hashCode()) + "000000000";
        eanLegado = eanLegado.substring(0, 13);
        mvc.perform(post("/api/products").header("Authorization", farmacia).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("gtin", eanLegado, "name", "Produto legado " + sufixo))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ean").value(eanLegado))
            .andExpect(jsonPath("$.gtin").doesNotExist());

        String nomeAmbiguo = "Vitamina ambígua " + sufixo;
        mvc.perform(post("/api/products").header("Authorization", farmacia).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("ean", "11111" + eanLegado.substring(5), "name", nomeAmbiguo))))
            .andExpect(status().isOk());
        mvc.perform(post("/api/products").header("Authorization", farmacia).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("ean", "22222" + eanLegado.substring(5), "name", nomeAmbiguo))))
            .andExpect(status().isOk());
        MockMultipartFile nomeDuplicado = new MockMultipartFile("file", "nome-ambiguo.csv", "text/csv",
            ("ean,produto,quantidade\n," + nomeAmbiguo + ",2\n").getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/quotations/import/preview").file(nomeDuplicado).header("Authorization", farmacia))
            .andExpect(status().isOk()).andExpect(jsonPath("$.invalidRows").value(1))
            .andExpect(jsonPath("$.lines[0].errors[0]").value("Há mais de um produto com este nome. Informe o EAN para diferenciá-lo."));

        MockMultipartFile eanInvalido = new MockMultipartFile("file", "ean-invalido.csv", "text/csv",
            "ean,produto,quantidade\nABC,Produto inválido,1\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/quotations/import/preview").file(eanInvalido).header("Authorization", farmacia))
            .andExpect(status().isOk()).andExpect(jsonPath("$.invalidRows").value(1))
            .andExpect(jsonPath("$.lines[0].errors[0]").value("EAN deve conter de 8 a 14 dígitos."));
    }

    @Test
    void importaPlanilhaPorCabecalhosPermiteRemapeamentoEPreenchimentoManual() throws Exception {
        String farmacia = loginFarmacia("admin@cotapreco.local", "Cotapreco@123");
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        byte[] arquivo = pedidoXlsx(sufixo);

        MockMultipartFile analise = new MockMultipartFile("file", "PEDIDO.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", arquivo);
        mvc.perform(multipart("/api/quotations/import/analyze").file(analise).header("Authorization", farmacia))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sheetName").value("data"))
            .andExpect(jsonPath("$.totalRows").value(2))
            .andExpect(jsonPath("$.columns.length()").value(8))
            .andExpect(jsonPath("$.suggestedMapping.productName").value(4))
            .andExpect(jsonPath("$.suggestedMapping.quantity").value(5))
            .andExpect(jsonPath("$.suggestedMapping.ean").value(6))
            .andExpect(jsonPath("$.suggestedMapping.laboratory").value(7))
            .andExpect(jsonPath("$.sampleRows[0][4]").value("Produto PEDIDO " + sufixo));

        MockMultipartFile previaArquivo = new MockMultipartFile("file", "PEDIDO.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", arquivo);
        MockMultipartFile mapeamento = new MockMultipartFile("mapping", "mapping.json", MediaType.APPLICATION_JSON_VALUE,
            mapper.writeValueAsBytes(Map.of("ean", 6, "productName", 4, "quantity", 5, "laboratory", 7)));
        mvc.perform(multipart("/api/quotations/import/preview").file(previaArquivo).file(mapeamento).header("Authorization", farmacia))
            .andExpect(status().isOk()).andExpect(jsonPath("$.validRows").value(2))
            .andExpect(jsonPath("$.lines[0].ean").value("7891234567890"))
            .andExpect(jsonPath("$.lines[0].productName").value("Produto PEDIDO " + sufixo))
            .andExpect(jsonPath("$.lines[0].quantity").value(12))
            .andExpect(jsonPath("$.lines[0].laboratory").value("Laboratório A"))
            .andExpect(jsonPath("$.lines[1].ean").value("7899876543210"));

        MockMultipartFile arquivoDuplicado = new MockMultipartFile("file", "PEDIDO.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", arquivo);
        MockMultipartFile mapeamentoDuplicado = new MockMultipartFile("mapping", "mapping.json", MediaType.APPLICATION_JSON_VALUE,
            mapper.writeValueAsBytes(Map.of("ean", 6, "productName", 4, "quantity", 4)));
        mvc.perform(multipart("/api/quotations/import/preview").file(arquivoDuplicado).file(mapeamentoDuplicado)
            .header("Authorization", farmacia)).andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.message").value("Cada campo deve usar uma coluna diferente."));

        String manual = mapper.writeValueAsString(Map.of("items", List.of(
            Map.of("row", 1, "ean", "7899876543210", "productName", "Produto manual " + sufixo,
                "quantity", "3", "laboratory", "Laboratório Manual"),
            Map.of("row", 2, "ean", "7899876543210", "productName", "Produto manual " + sufixo,
                "quantity", "2", "laboratory", "Laboratório Manual"))));
        mvc.perform(post("/api/quotations/items/preview").header("Authorization", farmacia)
            .contentType(MediaType.APPLICATION_JSON).content(manual))
            .andExpect(status().isOk()).andExpect(jsonPath("$.invalidRows").value(2))
            .andExpect(jsonPath("$.lines[0].errors[0]").value("Produto duplicado na lista."));

        Long empresaId = usuarios.findByEmailIgnoreCase("admin@cotapreco.local").orElseThrow().getEmpresa().getId();
        String eanExistente = eanAleatorio(), eanNovo = eanAleatorio();
        mvc.perform(post("/api/products").header("Authorization", farmacia).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("ean", eanExistente, "name", "Produto existente " + sufixo,
                "laboratory", "Laboratório Original"))))
            .andExpect(status().isOk());
        String cotacao = mapper.writeValueAsString(Map.of("name", "Cotação laboratório " + sufixo, "items", List.of(
            Map.of("ean", eanExistente, "productName", "Produto existente " + sufixo, "quantity", 1, "laboratory", "Não sobrescrever"),
            Map.of("ean", eanNovo, "productName", "Produto novo " + sufixo, "quantity", 2, "laboratory", "Laboratório Novo"))));
        mvc.perform(post("/api/quotations").header("Authorization", farmacia).contentType(MediaType.APPLICATION_JSON).content(cotacao))
            .andExpect(status().isOk());
        assertThat(produtos.findByEmpresaIdAndEan(empresaId, eanExistente).orElseThrow().getLaboratorio()).isEqualTo("Laboratório Original");
        assertThat(produtos.findByEmpresaIdAndEan(empresaId, eanNovo).orElseThrow().getLaboratorio()).isEqualTo("Laboratório Novo");
    }

    @Test
    void geraModeloExcelVazioComEanComoTexto() throws Exception {
        String farmacia = loginFarmacia("admin@cotapreco.local", "Cotapreco@123");
        byte[] modelo = mvc.perform(get("/api/quotations/import/template").header("Authorization", farmacia))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"modelo-cotacao-cotapreco.xlsx\""))
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(modelo))) {
            var sheet = workbook.getSheet("Produtos");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getLastRowNum()).isZero();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("EAN");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Produto");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Quantidade");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Laboratório");
            assertThat(sheet.getColumnStyle(0).getDataFormatString()).isEqualTo("@");
        }
    }

    @Test
    void loginDoRepresentanteNormalizaTelefoneERecuperacaoNaoRevelaConta() throws Exception {
        String autenticacaoFarmacia = loginFarmacia("admin@cotapreco.local", "Cotapreco@123");
        String tokenCotacao = criarEAbrirCotacao(autenticacaoFarmacia, "Cotação para login");
        String sufixo = UUID.randomUUID().toString();
        String telefone = ("81" + sufixo.replaceAll("\\D", "") + "000000000").substring(0, 11);
        cadastrarRepresentante(tokenCotacao, "Carla Mendes", telefone, "carla-" + sufixo + "@teste.local");
        mvc.perform(post("/api/publico/representantes/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("telefone", "+55 " + telefone, "senha", "Senha123"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.representante.nome").value("Carla Mendes"));
        mvc.perform(post("/api/publico/representantes/esqueci-senha").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", "inexistente@teste.local"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void representantePodeUsarSenhaComUmUnicoCaractere() throws Exception {
        String autenticacaoFarmacia = loginFarmacia("admin@cotapreco.local", "Cotapreco@123");
        String tokenCotacao = criarEAbrirCotacao(autenticacaoFarmacia, "Cotação com senha simples");
        mvc.perform(post("/api/publico/representantes/cadastro").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("tokenCotacao", tokenCotacao, "nome", "Senha Simples", "telefone", "81999992001",
                "email", "senha-simples@teste.local", "senha", "1"))))
            .andExpect(status().isOk());
        mvc.perform(post("/api/publico/representantes/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("telefone", "81999992001", "senha", "1"))))
            .andExpect(status().isOk());
    }

    @Test
    void representantePodeAlterarAPropriaSenhaEInvalidaSessaoAnterior() throws Exception {
        String autenticacaoFarmacia = loginFarmacia("admin@cotapreco.local", "Cotapreco@123");
        String tokenCotacao = criarEAbrirCotacao(autenticacaoFarmacia, "Cotação para alterar senha");
        String sessaoAnterior = cadastrarRepresentante(tokenCotacao, "Lara Alves", "81999994002", "lara4002@teste.local");

        mvc.perform(put("/api/publico/representantes/senha").header("Authorization", sessaoAnterior).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("senhaAtual", "SenhaErrada123", "novaSenha", "NovaSenha456"))))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value("A senha atual está incorreta."));
        mvc.perform(put("/api/publico/representantes/senha").header("Authorization", sessaoAnterior).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("senhaAtual", "Senha123", "novaSenha", "NovaSenha456"))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/publico/representantes/eu").header("Authorization", sessaoAnterior)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/publico/representantes/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("telefone", "81999994002", "senha", "NovaSenha456"))))
            .andExpect(status().isOk());
    }

    @Test
    void desativaProdutoERespostaSemApagarHistoricoDaCotacao() throws Exception {
        String autenticacaoFarmacia = loginFarmacia("admin@cotapreco.local", "Cotapreco@123");
        String tokenCotacao = criarEAbrirCotacao(autenticacaoFarmacia, "Cotação com exclusões");
        String sufixo = UUID.randomUUID().toString();
        String telefone = ("81" + sufixo.replaceAll("\\D", "") + "000000000").substring(0, 11);
        String autenticacaoRepresentante = cadastrarRepresentante(tokenCotacao, "Representante", telefone, "exclusao-" + sufixo + "@teste.local");
        JsonNode resposta = criarProposta(tokenCotacao, autenticacaoRepresentante, "Distribuidora removível", null);
        long respostaId = resposta.get("id").asLong();
        long primeiroItemResposta = resposta.at("/itens/0/id").asLong();
        long segundoItemResposta = resposta.at("/itens/1/id").asLong();
        atualizarEEnviar(tokenCotacao, respostaId, autenticacaoRepresentante,
            respostaJson("Distribuidora removível", null, primeiroItemResposta, segundoItemResposta, 10, 100, 5, 50));
        long cotacaoId = localizarCotacaoPorToken(tokenCotacao);
        JsonNode cotacao = json(mvc.perform(get("/api/quotations/{id}", cotacaoId).header("Authorization", autenticacaoFarmacia))
            .andExpect(status().isOk()).andReturn());
        long itemCotacaoId = cotacao.at("/items/0/id").asLong();
        mvc.perform(put("/api/quotations/{id}/items/{itemId}", cotacaoId, itemCotacaoId).header("Authorization", autenticacaoFarmacia)
            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":120,\"active\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.requestedQuantity").value(120)).andExpect(jsonPath("$.active").value(true));
        mvc.perform(post("/api/quotations/{id}/close", cotacaoId).header("Authorization", autenticacaoFarmacia)).andExpect(status().isOk());
        mvc.perform(put("/api/quotations/{id}/items/{itemId}", cotacaoId, itemCotacaoId).header("Authorization", autenticacaoFarmacia)
            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":120,\"active\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/publico/cotacoes/{token}", tokenCotacao)).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalProdutos").value(1));
        mvc.perform(get("/api/quotations/{id}/comparison", cotacaoId).header("Authorization", autenticacaoFarmacia))
            .andExpect(status().isOk()).andExpect(jsonPath("$.products.length()").value(1));
        mvc.perform(put("/api/quotations/{id}/responses/{responseId}/active", cotacaoId, respostaId).header("Authorization", autenticacaoFarmacia)
            .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/quotations/{id}/comparison", cotacaoId).header("Authorization", autenticacaoFarmacia))
            .andExpect(status().isOk()).andExpect(jsonPath("$.supplierTotals.length()").value(0));
    }

    @Test
    void usuarioAlteraSenhaInformandoASenhaAtual() throws Exception {
        Usuario administrador = usuarios.findByEmailIgnoreCase("admin@cotapreco.local").orElseThrow();
        String email = "senha-" + UUID.randomUUID() + "@teste.local";
        Usuario usuario = new Usuario();
        usuario.setEmpresa(administrador.getEmpresa());
        usuario.setNome("Usuário da senha");
        usuario.setEmail(email);
        usuario.setSenhaHash(codificador.encode("SenhaAntiga123"));
        usuario.setPerfil(PerfilUsuario.BUYER);
        usuarios.save(usuario);
        String autenticacao = loginFarmacia(email, "SenhaAntiga123");

        mvc.perform(put("/api/auth/password").header("Authorization", autenticacao).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("senhaAtual", "SenhaErrada123", "novaSenha", "SenhaNova456"))))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value("A senha atual está incorreta."));
        mvc.perform(put("/api/auth/password").header("Authorization", autenticacao).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("senhaAtual", "SenhaAntiga123", "novaSenha", "SenhaNova456"))))
            .andExpect(status().isNoContent());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", email, "password", "SenhaAntiga123"))))
            .andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", email, "password", "SenhaNova456"))))
            .andExpect(status().isOk());
    }

    @Test
    void redefinicaoDeSenhaEDeUsoUnicoEInvalidaSessaoAnterior() throws Exception {
        String autenticacaoFarmacia = loginFarmacia("admin@cotapreco.local", "Cotapreco@123");
        String tokenCotacao = criarEAbrirCotacao(autenticacaoFarmacia, "Cotação para redefinição");
        String sessaoAnterior = cadastrarRepresentante(tokenCotacao, "Daniel Rocha", "81999994001", "daniel4001@teste.local");
        Representante representante = representantes.findByTelefone("81999994001").orElseThrow();
        String tokenAberto = "token-de-redefinicao-para-o-teste";
        TokenRedefinicaoSenhaRepresentante registro = new TokenRedefinicaoSenhaRepresentante();
        registro.setRepresentante(representante);
        registro.setTokenHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(tokenAberto.getBytes(StandardCharsets.UTF_8))));
        registro.setExpiraEm(Instant.now().plusSeconds(600));
        tokensRedefinicao.save(registro);

        mvc.perform(post("/api/publico/representantes/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("token", tokenAberto, "novaSenha", "NovaSenha456"))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/publico/representantes/eu").header("Authorization", sessaoAnterior)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/publico/representantes/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("telefone", "81999994001", "senha", "NovaSenha456"))))
            .andExpect(status().isOk());
        mvc.perform(post("/api/publico/representantes/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("token", tokenAberto, "novaSenha", "OutraSenha789"))))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void usuarioPodeRedefinirSenhaComTokenDeUsoUnicoEInvalidaSessaoAnterior() throws Exception {
        Usuario administrador = usuarios.findByEmailIgnoreCase("admin@cotapreco.local").orElseThrow();
        String email = "recuperacao-" + UUID.randomUUID() + "@teste.local";
        Usuario usuario = new Usuario();
        usuario.setEmpresa(administrador.getEmpresa());
        usuario.setNome("Usuário em recuperação");
        usuario.setEmail(email);
        usuario.setSenhaHash(codificador.encode("SenhaAntiga123"));
        usuario.setPerfil(PerfilUsuario.BUYER);
        usuarios.save(usuario);
        String sessaoAnterior = loginFarmacia(email, "SenhaAntiga123");
        String tokenAberto = "token-usuario-redefinicao-para-o-teste";
        TokenRedefinicaoSenhaUsuario registro = new TokenRedefinicaoSenhaUsuario();
        registro.setUsuario(usuario);
        registro.setTokenHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(tokenAberto.getBytes(StandardCharsets.UTF_8))));
        registro.setExpiraEm(Instant.now().plusSeconds(600));
        tokensRedefinicaoUsuarios.save(registro);

        mvc.perform(post("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("token", tokenAberto, "novaSenha", "NovaSenha456"))))
            .andExpect(status().isOk());
        mvc.perform(get("/api/auth/me").header("Authorization", sessaoAnterior)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", email, "password", "NovaSenha456"))))
            .andExpect(status().isOk());
        mvc.perform(post("/api/auth/redefinir-senha").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("token", tokenAberto, "novaSenha", "OutraSenha789"))))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void usuarioNaoAcessaCotacaoDeOutraEmpresa() throws Exception {
        String admin = loginFarmacia("admin@cotapreco.local", "Cotapreco@123");
        JsonNode propria = json(mvc.perform(post("/api/quotations").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Cotação isolada\",\"items\":[{\"ean\":\"7890000000010\",\"productName\":\"Produto X\",\"quantity\":10}]}"))
            .andExpect(status().isOk()).andReturn());
        Empresa outra = new Empresa(); outra.setNome("Farmácia Dois"); outra.setSlug("farmacia-dois-" + UUID.randomUUID()); empresas.save(outra);
        Usuario usuario = new Usuario(); usuario.setEmpresa(outra); usuario.setNome("Comprador Dois"); usuario.setEmail("buyer2@cotapreco.local"); usuario.setSenhaHash(codificador.encode("Senha@123")); usuario.setPerfil(PerfilUsuario.BUYER); usuarios.save(usuario);
        String autenticacaoOutra = loginFarmacia("buyer2@cotapreco.local", "Senha@123");
        mvc.perform(get("/api/quotations/{id}", propria.get("id").asLong()).header("Authorization", autenticacaoOutra)).andExpect(status().isNotFound());
    }

    @Test
    void cotacaoExpiradaNaoPermiteCadastroNemCorrecao() throws Exception {
        String autenticacaoFarmacia = loginFarmacia("admin@cotapreco.local", "Cotapreco@123");
        String token = criarEAbrirCotacao(autenticacaoFarmacia, "Cotação com prazo");
        Cotacao entidade = cotacoes.findByTokenPublico(token).orElseThrow();
        entidade.setExpiraEm(Instant.now().minusSeconds(60)); cotacoes.save(entidade);
        mvc.perform(get("/api/publico/cotacoes/{token}", token)).andExpect(status().isOk()).andExpect(jsonPath("$.aceitaRespostas").value(false));
        mvc.perform(post("/api/publico/representantes/cadastro").contentType(MediaType.APPLICATION_JSON)
            .content(cadastroJson(token, "Expirado", "81999993001", "expirado@teste.local")))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void laboratorioSolicitadoEExibidoAoRepresentanteComFallbackParaCatalogo() throws Exception {
        String autenticacao = loginFarmacia("admin@cotapreco.local", "Cotapreco@123");
        String conteudo = mapper.writeValueAsString(Map.of("name", "Cotação com laboratório", "items", List.of(
            Map.of("ean", "7890000000099", "productName", "Produto solicitado", "quantity", 10, "laboratory", "Laboratório do comprador"))));
        JsonNode rascunho = json(mvc.perform(post("/api/quotations").header("Authorization", autenticacao)
            .contentType(MediaType.APPLICATION_JSON).content(conteudo)).andExpect(status().isOk()).andReturn());
        JsonNode aberta = json(mvc.perform(post("/api/quotations/{id}/open", rascunho.get("id").asLong()).header("Authorization", autenticacao))
            .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].laboratory").value("Laboratório do comprador")).andReturn());
        String token = aberta.get("publicToken").asText();
        mvc.perform(get("/api/publico/cotacoes/{token}", token)).andExpect(status().isOk())
            .andExpect(jsonPath("$.itens[0].laboratorio").value("Laboratório do comprador"));

        String representante = cadastrarRepresentante(token, "Laboratório Rep", "81999993101", "laboratorio-rep@teste.local");
        JsonNode proposta = criarProposta(token, representante, "Distribuidora Laboratório", null);
        mvc.perform(get("/api/publico/cotacoes/{token}/respostas/{id}", token, proposta.get("id").asLong()).header("Authorization", representante))
            .andExpect(status().isOk()).andExpect(jsonPath("$.itens[0].laboratorio").value("Laboratório do comprador"));

        String tokenLegado = criarEAbrirCotacao(autenticacao, "Cotação catálogo");
        Long empresaId = usuarios.findByEmailIgnoreCase("admin@cotapreco.local").orElseThrow().getEmpresa().getId();
        Produto produto = produtos.findByEmpresaIdAndEan(empresaId, "7890000000001").orElseThrow();
        produto.setLaboratorio("Laboratório do catálogo");
        produtos.save(produto);
        mvc.perform(get("/api/publico/cotacoes/{token}", tokenLegado)).andExpect(status().isOk())
            .andExpect(jsonPath("$.itens[0].laboratorio").value("Laboratório do catálogo"));
    }

    @Test
    void prorrogaCotacaoReabreFechadaEAumentaVersaoDoCompartilhamento() throws Exception {
        String autenticacao = loginFarmacia("admin@cotapreco.local", "Cotapreco@123");
        String token = criarEAbrirCotacao(autenticacao, "Cotação prorrogável");
        long cotacaoId = localizarCotacaoPorToken(token);
        Instant primeiroPrazo = Instant.now().plusSeconds(3600);

        mvc.perform(put("/api/quotations/{id}/expiration", cotacaoId).header("Authorization", autenticacao)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("expiresAt", primeiroPrazo.toString()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.publicUrl").value(org.hamcrest.Matchers.containsString("?v=" + primeiroPrazo.toEpochMilli())));
        mvc.perform(get("/api/publico/cotacoes/{token}/compartilhar", token))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/compartilhar?v=" + primeiroPrazo.toEpochMilli())))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("/imagem-compartilhamento?v=" + primeiroPrazo.toEpochMilli())));

        mvc.perform(post("/api/quotations/{id}/close", cotacaoId).header("Authorization", autenticacao)).andExpect(status().isOk());
        Instant segundoPrazo = Instant.now().plusSeconds(7200);
        mvc.perform(put("/api/quotations/{id}/expiration", cotacaoId).header("Authorization", autenticacao)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("expiresAt", segundoPrazo.toString()))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"));

        mvc.perform(put("/api/quotations/{id}/expiration", cotacaoId).header("Authorization", autenticacao)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("expiresAt", Instant.now().minusSeconds(60).toString()))))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void corsLiberaSomenteFrontendConfigurado() throws Exception {
        mvc.perform(options("/api/publico/representantes/login")
            .header("Origin", "http://localhost:5173")
            .header("Access-Control-Request-Method", "POST")
            .header("Access-Control-Request-Headers", "authorization,content-type"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/publico/representantes/login")
            .header("Origin", "https://origem-invalida.example")
            .header("Access-Control-Request-Method", "POST"))
            .andExpect(status().isForbidden())
            .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void cadastraFarmaciaComAdministradorECriaUsuarioDaEquipe() throws Exception {
        String sufixo = UUID.randomUUID().toString().replaceAll("\\D", "");
        String cnpj = ("99" + sufixo + "00000000000000").substring(0, 14);
        JsonNode cadastro = json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("nomeUsuario", "Nova Administradora", "nomeFarmacia", "Farmácia Nova", "cnpj", cnpj,
                "email", "admin-" + UUID.randomUUID() + "@teste.local", "senha", "Senha@123"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("ADMIN"))
            .andExpect(jsonPath("$.user.companyName").value("Farmácia Nova")).andReturn());
        String autenticacao = "Bearer " + cadastro.get("token").asText();
        mvc.perform(get("/api/users").header("Authorization", autenticacao))
            .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(post("/api/users").header("Authorization", autenticacao).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("nome", "Comprador da Nova", "email", "comprador-" + UUID.randomUUID() + "@teste.local",
                "senha", "Senha@123", "perfil", "BUYER"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("BUYER"));
    }

    private String criarEAbrirCotacao(String autenticacao, String nome) throws Exception {
        MockMultipartFile csv = new MockMultipartFile("file", "compras.csv", "text/csv",
            "ean,produto,quantidade\n7890000000001,Dipirona 500mg,100\n7890000000002,Paracetamol 750mg,50\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/quotations/import/preview").file(csv).header("Authorization", autenticacao))
            .andExpect(status().isOk()).andExpect(jsonPath("$.validRows").value(2));
        String conteudo = mapper.writeValueAsString(Map.of("name", nome, "items", List.of(
            Map.of("ean", "7890000000001", "productName", "Dipirona 500mg", "quantity", 100),
            Map.of("ean", "7890000000002", "productName", "Paracetamol 750mg", "quantity", 50))));
        JsonNode rascunho = json(mvc.perform(post("/api/quotations").header("Authorization", autenticacao).contentType(MediaType.APPLICATION_JSON)
            .content(conteudo))
            .andExpect(status().isOk()).andReturn());
        JsonNode aberta = json(mvc.perform(post("/api/quotations/{id}/open", rascunho.get("id").asLong()).header("Authorization", autenticacao))
            .andExpect(status().isOk()).andReturn());
        return aberta.get("publicToken").asText();
    }

    private String cadastrarRepresentante(String tokenCotacao, String nome, String telefone, String email) throws Exception {
        JsonNode resultado = json(mvc.perform(post("/api/publico/representantes/cadastro").contentType(MediaType.APPLICATION_JSON)
            .content(cadastroJson(tokenCotacao, nome, telefone, email))).andExpect(status().isOk()).andReturn());
        return "Bearer " + resultado.get("token").asText();
    }
    private String cadastroJson(String token, String nome, String telefone, String email) throws Exception {
        return mapper.writeValueAsString(Map.of("tokenCotacao", token, "nome", nome, "telefone", telefone, "email", email, "senha", "Senha123"));
    }
    private JsonNode criarProposta(String token, String autenticacao, String nome, String documento) throws Exception {
        return json(mvc.perform(post("/api/publico/cotacoes/{token}/respostas", token).header("Authorization", autenticacao)
            .contentType(MediaType.APPLICATION_JSON).content(distribuidoraJson(nome, documento))).andExpect(status().isOk()).andReturn());
    }
    private String distribuidoraJson(String nome, String documento) throws Exception {
        Map<String,Object> dados = new LinkedHashMap<>(); dados.put("nomeDistribuidora", nome); dados.put("documentoDistribuidora", documento); return mapper.writeValueAsString(dados);
    }
    private void atualizarEEnviar(String token, long id, String autenticacao, String conteudo) throws Exception {
        mvc.perform(put("/api/publico/cotacoes/{token}/respostas/{id}", token, id).header("Authorization", autenticacao)
            .contentType(MediaType.APPLICATION_JSON).content(conteudo)).andExpect(status().isOk());
        mvc.perform(post("/api/publico/cotacoes/{token}/respostas/{id}/enviar", token, id).header("Authorization", autenticacao))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
    }
    private String respostaJson(String nome, String documento, long primeiro, long segundo, Integer preco1, Integer quantidade1, Integer preco2, Integer quantidade2) throws Exception {
        Map<String,Object> dados = new LinkedHashMap<>(); dados.put("nomeDistribuidora", nome); dados.put("documentoDistribuidora", documento);
        dados.put("itens", List.of(item(primeiro, preco1, quantidade1), item(segundo, preco2, quantidade2))); return mapper.writeValueAsString(dados);
    }
    @SafeVarargs
    private final String respostaJson(String nome, String documento, Map<String,Object>... itens) throws Exception {
        Map<String,Object> dados = new LinkedHashMap<>(); dados.put("nomeDistribuidora", nome); dados.put("documentoDistribuidora", documento);
        dados.put("itens", List.of(itens)); return mapper.writeValueAsString(dados);
    }
    private Map<String,Object> item(long id, Integer preco, Integer quantidade) {
        Map<String,Object> item = new LinkedHashMap<>(); item.put("id", id); item.put("disponivel", preco != null); item.put("precoUnitario", preco); item.put("quantidadeDisponivel", quantidade); item.put("observacao", null); return item;
    }
    private Map<String,Object> itemDisponivel(long id, Integer preco, Integer quantidade, String observacao) {
        Map<String,Object> item = new LinkedHashMap<>(); item.put("id", id); item.put("disponivel", true);
        item.put("precoUnitario", preco); item.put("quantidadeDisponivel", quantidade); item.put("observacao", observacao); return item;
    }
    private Map<String,Object> itemIndisponivel(long id) {
        Map<String,Object> item = new LinkedHashMap<>(); item.put("id", id); item.put("disponivel", false);
        item.put("precoUnitario", null); item.put("quantidadeDisponivel", null); item.put("observacao", null); return item;
    }
    private Map<String,Object> itemPlano(long itemId, int desejada, Long respostaId, Integer quantidadeCampeao, String justificativa, boolean manual) {
        Map<String,Object> item = new LinkedHashMap<>(); item.put("quotationItemId", itemId); item.put("desiredQuantity", desejada);
        item.put("selectedResponseId", respostaId); item.put("championQuantity", quantidadeCampeao); item.put("stockOverrideNote", justificativa); item.put("manualSelection", manual); return item;
    }
    private byte[] pedidoXlsx(String sufixo) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("data");
            var header = sheet.createRow(0);
            String[] headings = {"Data", "Pedido", "CNPJ Filial", "Cod Reduzido", "Descricao", "Quantidade", "EAN Principal", "Laboratorio"};
            for (int index = 0; index < headings.length; index++) header.createCell(index).setCellValue(headings[index]);
            var first = sheet.createRow(1);
            first.createCell(0).setCellValue("06/08/2026"); first.createCell(1).setCellValue(65);
            first.createCell(2).setCellValue("12.345.678/0001-90"); first.createCell(3).setCellValue(101);
            first.createCell(4).setCellValue("Produto PEDIDO " + sufixo); first.createCell(5).setCellValue(12);
            first.createCell(6).setCellValue(7891234567890d); first.createCell(7).setCellValue("Laboratório A");
            var second = sheet.createRow(2);
            second.createCell(1).setCellValue(66); second.createCell(4).setCellValue("Outro produto " + sufixo);
            second.createCell(5).setCellValue(4); second.createCell(6).setCellValue("7.899876543210E12");
            second.createCell(7).setCellValue("Laboratório B");
            workbook.write(output);
            return output.toByteArray();
        }
    }
    private String eanAleatorio() {
        return ("789" + UUID.randomUUID().toString().replaceAll("\\D", "") + "0000000000000").substring(0, 13);
    }
    private String loginFarmacia(String email, String senha) throws Exception {
        JsonNode corpo = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", email, "password", senha)))).andExpect(status().isOk()).andReturn());
        return "Bearer " + corpo.get("token").asText();
    }
    private long localizarCotacaoPorToken(String token) { return cotacoes.findByTokenPublico(token).orElseThrow().getId(); }
    private JsonNode json(org.springframework.test.web.servlet.MvcResult resultado) throws Exception { return mapper.readTree(resultado.getResponse().getContentAsString()); }
}
