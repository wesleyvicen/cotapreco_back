package br.com.cotapreco;

import br.com.cotapreco.enums.PerfilUsuario;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class FluxoCotaPrecoIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired EmpresaRepository companies;
    @Autowired UsuarioRepository users;
    @Autowired CotacaoRepository quotations;
    @Autowired PasswordEncoder encoder;

    @Test
    void fluxoCompletoDaCotacaoComImportacaoRespostasEComparacao() throws Exception {
        String auth = login("admin@cotapreco.local", "Cotapreco@123");

        MockMultipartFile csv = new MockMultipartFile("file", "compras.csv", "text/csv",
            "gtin,produto,quantidade\n7890000000001,Dipirona 500mg,100\n7890000000002,Paracetamol 750mg,50\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/quotations/import/preview").file(csv).header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.validRows").value(2)).andExpect(jsonPath("$.invalidRows").value(0));

        JsonNode draft = json(mvc.perform(post("/api/quotations").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"Reposição agosto","items":[
                  {"gtin":"7890000000001","productName":"Dipirona 500mg","quantity":100},
                  {"gtin":"7890000000002","productName":"Paracetamol 750mg","quantity":50}
                ]}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andExpect(jsonPath("$.publicToken").isEmpty()).andReturn());
        long quotationId = draft.get("id").asLong();

        JsonNode opened = json(mvc.perform(post("/api/quotations/{id}/open", quotationId).header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN")).andReturn());
        String publicToken = opened.get("publicToken").asText();
        assertThat(publicToken).hasSizeGreaterThanOrEqualTo(40).doesNotContain("/");

        mvc.perform(get("/api/public/quotations/{token}", publicToken)).andExpect(status().isOk())
            .andExpect(jsonPath("$.companyName").value("Farmácia Exemplo")).andExpect(jsonPath("$.productCount").value(2))
            .andExpect(jsonPath("$.id").doesNotExist()).andExpect(jsonPath("$.items[0].id").doesNotExist());

        String responseA = startResponse(publicToken, "Ana", "Distribuidora A");
        JsonNode detailA = json(mvc.perform(get("/api/public/responses/{token}", responseA)).andExpect(status().isOk()).andReturn());
        long a1 = detailA.at("/items/0/id").asLong(), a2 = detailA.at("/items/1/id").asLong();

        String responseB = startResponse(publicToken, "Bruno", "Distribuidora B");
        JsonNode detailB = json(mvc.perform(get("/api/public/responses/{token}", responseB)).andExpect(status().isOk()).andReturn());
        long b1 = detailB.at("/items/0/id").asLong(), b2 = detailB.at("/items/1/id").asLong();

        mvc.perform(put("/api/public/responses/{token}/items", responseB).contentType(MediaType.APPLICATION_JSON)
            .content(itemsJson(a1, a2, 9, 60, null, null))).andExpect(status().isUnprocessableEntity());

        updateAndSubmit(responseA, itemsJson(a1, a2, 10, 100, 5, 50));
        updateAndSubmit(responseB, itemsJson(b1, b2, 9, 60, null, null));

        mvc.perform(get("/api/quotations/{id}/responses", quotationId).header("Authorization", auth)).andExpect(status().isOk())
            .andExpect(jsonPath("$[0].status").value("SUBMITTED")).andExpect(jsonPath("$[1].status").value("SUBMITTED"));
        mvc.perform(get("/api/quotations/{id}/comparison", quotationId).header("Authorization", auth)).andExpect(status().isOk())
            .andExpect(jsonPath("$.products[0].winningSupplier").value("Distribuidora B"))
            .andExpect(jsonPath("$.products[0].coveredQuantity").value(100))
            .andExpect(jsonPath("$.bestCompositionTotal").value(1190.0))
            .andExpect(jsonPath("$.estimatedSavings").value(60.0));

        mvc.perform(post("/api/quotations/{id}/close", quotationId).header("Authorization", auth)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"));
        mvc.perform(post("/api/public/quotations/{token}/responses", publicToken).contentType(MediaType.APPLICATION_JSON).content(identityJson("Carlos", "Distribuidora C")))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void usuarioNaoAcessaCotacaoDeOutraEmpresa() throws Exception {
        String admin = login("admin@cotapreco.local", "Cotapreco@123");
        JsonNode own = json(mvc.perform(post("/api/quotations").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Cotação isolada\",\"items\":[{\"gtin\":\"7890000000010\",\"productName\":\"Produto X\",\"quantity\":10}]}"))
            .andExpect(status().isOk()).andReturn());

        Empresa other = new Empresa(); other.setNome("Farmácia Dois"); other.setSlug("farmacia-dois-" + UUID.randomUUID()); companies.save(other);
        Usuario otherUser = new Usuario(); otherUser.setEmpresa(other); otherUser.setNome("Comprador Dois"); otherUser.setEmail("buyer2@cotapreco.local"); otherUser.setSenhaHash(encoder.encode("Senha@123")); otherUser.setPerfil(PerfilUsuario.BUYER); users.save(otherUser);
        String otherAuth = login("buyer2@cotapreco.local", "Senha@123");

        mvc.perform(get("/api/quotations/{id}", own.get("id").asLong()).header("Authorization", otherAuth)).andExpect(status().isNotFound());
        mvc.perform(get("/api/quotations/{id}/comparison", own.get("id").asLong()).header("Authorization", otherAuth)).andExpect(status().isNotFound());
    }

    @Test
    void importacaoRejeitaLinhasInvalidasSemSalvarSilenciosamente() throws Exception {
        String auth = login("admin@cotapreco.local", "Cotapreco@123");
        MockMultipartFile csv = new MockMultipartFile("file", "invalida.csv", "text/csv",
            "gtin,produto,quantidade\n123,Produto inválido,0\n7890000000011,,abc\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/quotations/import/preview").file(csv).header("Authorization", auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.validRows").value(0)).andExpect(jsonPath("$.invalidRows").value(2))
            .andExpect(jsonPath("$.lines[0].errors").isNotEmpty());
    }

    @Test
    void cotacaoExpiradaNaoAceitaNovaResposta() throws Exception {
        String auth = login("admin@cotapreco.local", "Cotapreco@123");
        JsonNode draft = json(mvc.perform(post("/api/quotations").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Cotação com prazo\",\"expiresAt\":\"2099-08-10T12:00:00Z\",\"items\":[{\"gtin\":\"7890000000099\",\"productName\":\"Produto prazo\",\"quantity\":2}]}"))
            .andExpect(status().isOk()).andReturn());
        JsonNode opened = json(mvc.perform(post("/api/quotations/{id}/open", draft.get("id").asLong()).header("Authorization", auth)).andExpect(status().isOk()).andReturn());
        Cotacao entity = quotations.findById(draft.get("id").asLong()).orElseThrow(); entity.setExpiraEm(Instant.now().minusSeconds(60)); quotations.save(entity);
        String token = opened.get("publicToken").asText();
        mvc.perform(get("/api/public/quotations/{token}", token)).andExpect(status().isOk()).andExpect(jsonPath("$.acceptingResponses").value(false));
        mvc.perform(post("/api/public/quotations/{token}/responses", token).contentType(MediaType.APPLICATION_JSON).content(identityJson("Expirado", "Distribuidora")))
            .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.message").value("O prazo desta cotação expirou."));
    }

    private String login(String email, String password) throws Exception {
        JsonNode body = json(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("email", email, "password", password)))).andExpect(status().isOk()).andReturn());
        return "Bearer " + body.get("token").asText();
    }
    private String startResponse(String quotationToken, String representative, String supplier) throws Exception {
        JsonNode result = json(mvc.perform(post("/api/public/quotations/{token}/responses", quotationToken).contentType(MediaType.APPLICATION_JSON)
            .content(identityJson(representative, supplier))).andExpect(status().isOk()).andReturn()); return result.get("responseToken").asText();
    }
    private void updateAndSubmit(String token, String content) throws Exception {
        mvc.perform(put("/api/public/responses/{token}/items", token).contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isOk());
        mvc.perform(post("/api/public/responses/{token}/submit", token)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        mvc.perform(put("/api/public/responses/{token}/items", token).contentType(MediaType.APPLICATION_JSON).content(content)).andExpect(status().isUnprocessableEntity());
    }
    private String identityJson(String representative, String supplier) throws Exception { return mapper.writeValueAsString(Map.of("representativeName", representative, "supplierName", supplier, "phone", "81999999999")); }
    private String itemsJson(long first, long second, Integer price1, Integer qty1, Integer price2, Integer qty2) throws Exception {
        List<Map<String,Object>> items = new ArrayList<>(); items.add(item(first, price1, qty1)); items.add(item(second, price2, qty2)); return mapper.writeValueAsString(Map.of("items", items));
    }
    private Map<String,Object> item(long id, Integer price, Integer qty) { Map<String,Object> item = new LinkedHashMap<>(); item.put("id",id); item.put("available",price!=null); item.put("unitPrice",price); item.put("availableQuantity",qty); item.put("observation",null); return item; }
    private JsonNode json(org.springframework.test.web.servlet.MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsString()); }
}
