package br.com.cotapreco.service;

import br.com.cotapreco.exception.RegraNegocioException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class GeradorModeloImportacaoService {
    private static final String[] CABECALHOS = {"EAN", "Produto", "Quantidade", "Laboratório"};

    public byte[] gerar() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Produtos");
            Row header = sheet.createRow(0);
            header.setHeightInPoints(28);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.MEDIUM);
            headerStyle.setBottomBorderColor(IndexedColors.GOLD.getIndex());

            for (int column = 0; column < CABECALHOS.length; column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(CABECALHOS[column]);
                cell.setCellStyle(headerStyle);
            }

            CellStyle eanStyle = workbook.createCellStyle();
            eanStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));
            sheet.setDefaultColumnStyle(0, eanStyle);
            sheet.setColumnWidth(0, 18 * 256);
            sheet.setColumnWidth(1, 48 * 256);
            sheet.setColumnWidth(2, 16 * 256);
            sheet.setColumnWidth(3, 30 * 256);
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));

            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new RegraNegocioException("Não foi possível gerar o modelo Excel.");
        }
    }
}
