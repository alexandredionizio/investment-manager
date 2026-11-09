package com.investmanager.api.b3import;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class B3SpreadsheetPreviewServiceTest {
    private final B3SpreadsheetPreviewService service = new B3SpreadsheetPreviewService();

    @Test
    void previewsWithoutPersistingAndPreservesBothIdenticalTransactions() throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("Negociação");
            var h = sheet.createRow(0);
            String[] labels = {"Data do Negócio", "Tipo de Movimentação", "Mercado",
                    "Prazo/Vencimento", "Instituição", "Código de Negociação", "Quantidade", "Preço", "Valor"};
            for (int i = 0; i < labels.length; i++) h.createCell(i).setCellValue(labels[i]);
            for (int rowNo = 1; rowNo <= 2; rowNo++) {
                var row = sheet.createRow(rowNo);
                row.createCell(0).setCellValue("09/11/2026");
                row.createCell(1).setCellValue("Compra");
                row.createCell(2).setCellValue("Mercado à Vista");
                row.createCell(3).setCellValue("-");
                row.createCell(4).setCellValue("XP Investimentos");
                row.createCell(5).setCellValue("PETR4");
                row.createCell(6).setCellValue(100);
                row.createCell(7).setCellValue(49);
                row.createCell(8).setCellValue(4900);
            }
            book.write(out);
            var upload = new MockMultipartFile("file", "negociacao.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
            var result = service.preview(upload);
            assertEquals(2, result.totalRows());
            assertEquals(2, result.validRows());
            assertEquals(0, result.invalidRows());
            assertEquals(new BigDecimal("4900.0"), result.rows().get(0).totalValue());
            assertTrue(result.rows().get(0).warnings().isEmpty());
        }
    }

    @Test
    void rejectsNonExcelAndInvalidHeader() throws Exception {
        var fake = new MockMultipartFile("file", "test.txt", "text/plain", "abc".getBytes());
        assertThrows(IllegalArgumentException.class, () -> service.preview(fake));
    }
}
