package br.com.cotapreco.helper;

import br.com.cotapreco.exception.RegraNegocioException;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class LeitorArquivoImportacao {
    public record LinhaBruta(int row, String gtin, String name, String quantity) {}

    public List<LinhaBruta> parse(MultipartFile file) {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("").toLowerCase(Locale.ROOT);
        try {
            if (filename.endsWith(".csv")) return parseCsv(file.getInputStream());
            if (filename.endsWith(".xlsx")) return parseXlsx(file.getInputStream());
            throw new RegraNegocioException("Formato inválido. Envie um arquivo CSV ou XLSX.");
        } catch (IOException ex) { throw new RegraNegocioException("Não foi possível ler o arquivo enviado."); }
    }

    private List<LinhaBruta> parseCsv(InputStream input) throws IOException {
        List<LinhaBruta> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line; int row = 0; char delimiter = ',';
            while ((line = reader.readLine()) != null) {
                row++; if (row == 1) { delimiter = line.contains(";") ? ';' : ','; continue; }
                if (line.isBlank()) continue;
                List<String> cols = splitCsv(line, delimiter);
                result.add(new LinhaBruta(row, value(cols, 0), value(cols, 1), value(cols, 2)));
            }
        }
        return result;
    }
    private List<String> splitCsv(String line, char delimiter) {
        List<String> parts = new ArrayList<>(); StringBuilder current = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) { char c = line.charAt(i); if (c == '"') quoted = !quoted; else if (c == delimiter && !quoted) { parts.add(current.toString().trim()); current.setLength(0); } else current.append(c); }
        parts.add(current.toString().trim()); return parts;
    }
    private String value(List<String> values, int index) { return values.size() > index ? values.get(index).trim() : ""; }

    private List<LinhaBruta> parseXlsx(InputStream input) throws IOException {
        List<LinhaBruta> result = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0); DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) { Row row = sheet.getRow(i); if (row == null) continue;
                String gtin = formatter.formatCellValue(row.getCell(0)).trim(); String name = formatter.formatCellValue(row.getCell(1)).trim();
                String quantity = formatter.formatCellValue(row.getCell(2)).trim(); if (!gtin.isBlank() || !name.isBlank() || !quantity.isBlank()) result.add(new LinhaBruta(i + 1, gtin.replace(".0", ""), name, quantity));
            }
        } return result;
    }
}
