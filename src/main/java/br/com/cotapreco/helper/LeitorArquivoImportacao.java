package br.com.cotapreco.helper;

import br.com.cotapreco.exception.RegraNegocioException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;

@Component
public class LeitorArquivoImportacao {
    private static final int LIMITE_AMOSTRA = 5;
    private static final Set<String> CABECALHOS_EAN = Set.of("ean", "eanprincipal", "gtin");
    private static final Set<String> CABECALHOS_PRODUTO = Set.of("produto", "descricao", "nomedoproduto", "nomeproduto");
    private static final Set<String> CABECALHOS_QUANTIDADE = Set.of("quantidade", "qtd", "qtde");
    private static final Set<String> CABECALHOS_LABORATORIO = Set.of("laboratorio");

    public record LinhaBruta(int row, String ean, String name, String quantity, String laboratory) {}
    public record ColunaFonte(int index, String name) {}
    public record MapeamentoArquivo(Integer ean, Integer productName, Integer quantity, Integer laboratory) {}
    public record AnaliseArquivo(String sheetName, int totalRows, List<ColunaFonte> columns,
        MapeamentoArquivo suggestedMapping, List<List<String>> sampleRows) {}
    private record LinhaFonte(int row, List<String> values) {}
    private record ArquivoTabular(String sheetName, List<String> headers, List<LinhaFonte> rows) {}

    public AnaliseArquivo analisar(MultipartFile arquivo) {
        ArquivoTabular tabular = ler(arquivo);
        List<ColunaFonte> colunas = new ArrayList<>();
        for (int indice = 0; indice < tabular.headers().size(); indice++)
            colunas.add(new ColunaFonte(indice, tabular.headers().get(indice)));
        return new AnaliseArquivo(tabular.sheetName(), tabular.rows().size(), colunas, sugerir(tabular.headers()),
            tabular.rows().stream().limit(LIMITE_AMOSTRA).map(linha -> completar(linha.values(), tabular.headers().size())).toList());
    }

    public List<LinhaBruta> parse(MultipartFile arquivo) { return parse(arquivo, null); }

    public List<LinhaBruta> parse(MultipartFile arquivo, MapeamentoArquivo solicitado) {
        ArquivoTabular tabular = ler(arquivo);
        MapeamentoArquivo mapeamento = solicitado == null ? mapeamentoCompativel(tabular.headers()) : solicitado;
        validarMapeamento(mapeamento, tabular.headers().size());
        List<LinhaBruta> resultado = new ArrayList<>();
        for (LinhaFonte linha : tabular.rows()) {
            String ean = normalizarNumeroInteiro(valor(linha.values(), mapeamento.ean()));
            String nome = valor(linha.values(), mapeamento.productName());
            String quantidade = normalizarNumeroInteiro(valor(linha.values(), mapeamento.quantity()));
            String laboratorio = valor(linha.values(), mapeamento.laboratory());
            if (!ean.isBlank() || !nome.isBlank() || !quantidade.isBlank() || !laboratorio.isBlank())
                resultado.add(new LinhaBruta(linha.row(), ean, nome, quantidade, laboratorio));
        }
        return resultado;
    }

    private ArquivoTabular ler(MultipartFile arquivo) {
        String nome = Optional.ofNullable(arquivo.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        try {
            if (nome.endsWith(".csv")) return lerCsv(arquivo.getInputStream());
            if (nome.endsWith(".xlsx")) return lerXlsx(arquivo.getInputStream());
            throw new RegraNegocioException("Formato inválido. Envie um arquivo CSV ou XLSX.");
        } catch (RegraNegocioException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RegraNegocioException("Não foi possível ler o arquivo enviado.");
        }
    }

    private ArquivoTabular lerCsv(InputStream input) throws IOException {
        List<LinhaFonte> linhas = new ArrayList<>();
        List<String> cabecalhos = null;
        char delimitador = ',';
        int numeroLinha = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String texto;
            while ((texto = reader.readLine()) != null) {
                numeroLinha++;
                if (cabecalhos == null) {
                    if (texto.isBlank()) continue;
                    delimitador = texto.contains(";") ? ';' : ',';
                    cabecalhos = separarCsv(texto, delimitador);
                    continue;
                }
                if (texto.isBlank()) continue;
                linhas.add(new LinhaFonte(numeroLinha, separarCsv(texto, delimitador)));
            }
        }
        if (cabecalhos == null) throw new RegraNegocioException("O arquivo não possui cabeçalho.");
        return finalizar("CSV", cabecalhos, linhas);
    }

    private List<String> separarCsv(String linha, char delimitador) {
        List<String> partes = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean entreAspas = false;
        for (int indice = 0; indice < linha.length(); indice++) {
            char caractere = linha.charAt(indice);
            if (caractere == '"') {
                if (entreAspas && indice + 1 < linha.length() && linha.charAt(indice + 1) == '"') {
                    atual.append('"'); indice++;
                } else entreAspas = !entreAspas;
            } else if (caractere == delimitador && !entreAspas) {
                partes.add(atual.toString().trim()); atual.setLength(0);
            } else atual.append(caractere);
        }
        partes.add(atual.toString().trim());
        return partes;
    }

    private ArquivoTabular lerXlsx(InputStream input) throws IOException {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            DataFormatter formatter = new DataFormatter(new Locale("pt", "BR"));
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            for (Sheet planilha : workbook) {
                int linhaCabecalho = primeiraLinhaNaoVazia(planilha, formatter, evaluator);
                if (linhaCabecalho < 0) continue;
                Row cabecalho = planilha.getRow(linhaCabecalho);
                int totalColunas = Math.max(1, cabecalho.getLastCellNum());
                List<LinhaFonte> linhas = new ArrayList<>();
                for (int indice = linhaCabecalho + 1; indice <= planilha.getLastRowNum(); indice++) {
                    Row row = planilha.getRow(indice);
                    if (row == null) continue;
                    totalColunas = Math.max(totalColunas, Math.max(0, row.getLastCellNum()));
                    List<String> valores = lerLinha(row, totalColunas, formatter, evaluator);
                    if (valores.stream().anyMatch(valor -> !valor.isBlank())) linhas.add(new LinhaFonte(indice + 1, valores));
                }
                List<String> cabecalhos = lerLinha(cabecalho, totalColunas, formatter, evaluator);
                return finalizar(planilha.getSheetName(), cabecalhos, linhas);
            }
        }
        throw new RegraNegocioException("O arquivo não possui uma planilha com cabeçalho.");
    }

    private int primeiraLinhaNaoVazia(Sheet planilha, DataFormatter formatter, FormulaEvaluator evaluator) {
        for (int indice = planilha.getFirstRowNum(); indice <= planilha.getLastRowNum(); indice++) {
            Row linha = planilha.getRow(indice);
            if (linha != null && lerLinha(linha, Math.max(0, linha.getLastCellNum()), formatter, evaluator).stream().anyMatch(v -> !v.isBlank())) return indice;
        }
        return -1;
    }

    private List<String> lerLinha(Row linha, int totalColunas, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> valores = new ArrayList<>();
        for (int indice = 0; indice < totalColunas; indice++) valores.add(formatar(linha.getCell(indice), formatter, evaluator));
        return valores;
    }

    private String formatar(Cell celula, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (celula == null) return "";
        if (celula.getCellType() == CellType.NUMERIC && !DateUtil.isCellDateFormatted(celula))
            return BigDecimal.valueOf(celula.getNumericCellValue()).stripTrailingZeros().toPlainString();
        return formatter.formatCellValue(celula, evaluator).trim();
    }

    private ArquivoTabular finalizar(String planilha, List<String> cabecalhosOriginais, List<LinhaFonte> linhasOriginais) {
        int totalColunas = cabecalhosOriginais.size();
        for (LinhaFonte linha : linhasOriginais) totalColunas = Math.max(totalColunas, linha.values().size());
        List<String> cabecalhos = new ArrayList<>();
        for (int indice = 0; indice < totalColunas; indice++) {
            String nome = indice < cabecalhosOriginais.size() ? cabecalhosOriginais.get(indice).trim() : "";
            cabecalhos.add(nome.isBlank() ? "Coluna " + nomeColuna(indice) : nome);
        }
        int largura = totalColunas;
        List<LinhaFonte> linhas = linhasOriginais.stream()
            .map(linha -> new LinhaFonte(linha.row(), completar(linha.values(), largura))).toList();
        return new ArquivoTabular(planilha, cabecalhos, linhas);
    }

    private List<String> completar(List<String> valores, int tamanho) {
        List<String> resultado = new ArrayList<>(valores);
        while (resultado.size() < tamanho) resultado.add("");
        return List.copyOf(resultado);
    }

    private MapeamentoArquivo sugerir(List<String> cabecalhos) {
        return new MapeamentoArquivo(encontrar(cabecalhos, CABECALHOS_EAN), encontrar(cabecalhos, CABECALHOS_PRODUTO),
            encontrar(cabecalhos, CABECALHOS_QUANTIDADE), encontrar(cabecalhos, CABECALHOS_LABORATORIO));
    }

    private MapeamentoArquivo mapeamentoCompativel(List<String> cabecalhos) {
        MapeamentoArquivo sugerido = sugerir(cabecalhos);
        if (sugerido.productName() != null && sugerido.quantity() != null) return sugerido;
        if (cabecalhos.size() < 3) throw new RegraNegocioException("Não foi possível identificar as colunas de produto e quantidade.");
        return new MapeamentoArquivo(0, 1, 2, cabecalhos.size() > 3 ? 3 : null);
    }

    private Integer encontrar(List<String> cabecalhos, Set<String> aliases) {
        for (int indice = 0; indice < cabecalhos.size(); indice++) if (aliases.contains(normalizarCabecalho(cabecalhos.get(indice)))) return indice;
        return null;
    }

    private void validarMapeamento(MapeamentoArquivo mapeamento, int totalColunas) {
        if (mapeamento == null || mapeamento.productName() == null || mapeamento.quantity() == null)
            throw new RegraNegocioException("Selecione as colunas de produto e quantidade.");
        List<Integer> escolhidas = Arrays.asList(mapeamento.ean(), mapeamento.productName(), mapeamento.quantity(), mapeamento.laboratory())
            .stream().filter(Objects::nonNull).toList();
        if (new HashSet<>(escolhidas).size() != escolhidas.size()) throw new RegraNegocioException("Cada campo deve usar uma coluna diferente.");
        if (escolhidas.stream().anyMatch(indice -> indice < 0 || indice >= totalColunas)) throw new RegraNegocioException("O mapeamento contém uma coluna inválida.");
    }

    private String normalizarCabecalho(String valor) {
        return Normalizer.normalize(valor == null ? "" : valor, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String normalizarNumeroInteiro(String valor) {
        if (valor == null || valor.isBlank()) return "";
        String limpo = valor.trim();
        try {
            BigDecimal numero = new BigDecimal(limpo.replace(',', '.')).stripTrailingZeros();
            if (numero.scale() <= 0) return numero.toBigIntegerExact().toString();
        } catch (Exception ignored) {}
        return limpo;
    }

    private String valor(List<String> valores, Integer indice) { return indice == null || indice >= valores.size() ? "" : valores.get(indice).trim(); }
    private String nomeColuna(int indice) {
        StringBuilder nome = new StringBuilder();
        for (int numero = indice + 1; numero > 0; numero = (numero - 1) / 26) nome.insert(0, (char) ('A' + (numero - 1) % 26));
        return nome.toString();
    }
}
