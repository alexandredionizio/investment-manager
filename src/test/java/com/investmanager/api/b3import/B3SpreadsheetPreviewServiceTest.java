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

    private static final String[] B3_HEADERS = {
            "Data do Negócio", "Tipo de Movimentação", "Mercado", "Prazo/Vencimento",
            "Instituição", "Código de Negociação", "Quantidade", "Preço", "Valor"
    };

    private static org.apache.poi.ss.usermodel.Sheet initializedSheet(XSSFWorkbook book) {
        var sheet = book.createSheet("Negociação");
        var header = sheet.createRow(0);
        for (int i = 0; i < B3_HEADERS.length; i++) header.createCell(i).setCellValue(B3_HEADERS[i]);
        return sheet;
    }

    private static org.apache.poi.ss.usermodel.Row validRow(org.apache.poi.ss.usermodel.Sheet sheet, int rowIndex) {
        var row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue("09/11/2026");
        row.createCell(1).setCellValue("Compra");
        row.createCell(2).setCellValue("Mercado à Vista");
        row.createCell(3).setCellValue("-");
        row.createCell(4).setCellValue("XP Investimentos");
        row.createCell(5).setCellValue("petr4");
        row.createCell(6).setCellValue(100);
        row.createCell(7).setCellValue(49);
        row.createCell(8).setCellValue(4900);
        return row;
    }

    private static MockMultipartFile upload(XSSFWorkbook book) throws Exception {
        try (var out = new ByteArrayOutputStream()) {
            book.write(out);
            return new MockMultipartFile("file", "negociacoes.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test
    void rejectsCorruptXlsxAndMissingOrChangedHeader() throws Exception {
        var corrupt = new MockMultipartFile("file", "negociacoes.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "nao e excel".getBytes());
        assertThrows(IllegalArgumentException.class, () -> service.preview(corrupt));

        try (var book = new XSSFWorkbook()) {
            book.createSheet("Negociação");
            var ex = assertThrows(IllegalArgumentException.class, () -> service.preview(upload(book)));
            assertTrue(ex.getMessage().contains("Cabeçalho B3 ausente"));
        }
        try (var book = new XSSFWorkbook()) {
            var sheet = initializedSheet(book);
            sheet.getRow(0).getCell(5).setCellValue("Ativo");
            var ex = assertThrows(IllegalArgumentException.class, () -> service.preview(upload(book)));
            assertTrue(ex.getMessage().contains("coluna 6"));
        }
    }

    @Test
    void reportsInvalidValuesPerOriginalExcelRowWithoutLosingValidRows() throws Exception {
        try (var book = new XSSFWorkbook()) {
            var sheet = initializedSheet(book);
            validRow(sheet, 1);
            var invalid = validRow(sheet, 3);
            invalid.getCell(0).setCellValue("31/02/2026");
            invalid.getCell(1).setCellValue("TRANSFERENCIA");
            invalid.getCell(6).setCellValue(-4);
            var response = service.preview(upload(book));
            assertEquals(2, response.totalRows());
            assertEquals(1, response.validRows());
            assertEquals(1, response.invalidRows());
            assertEquals(2, response.rows().get(0).excelRow());
            assertEquals("PETR4", response.rows().get(0).ticker());
            var bad = response.rows().get(1);
            assertEquals(4, bad.excelRow());
            assertTrue(bad.errors().stream().anyMatch(s -> s.contains("Data inválida")));
            assertTrue(bad.errors().stream().anyMatch(s -> s.contains("Compra ou Venda")));
            assertTrue(bad.errors().stream().anyMatch(s -> s.contains("Quantidade")));
        }
    }

    @Test
    void rejectsFormulaCellsWithoutEvaluatingThem() throws Exception {
        try (var book = new XSSFWorkbook()) {
            var row = validRow(initializedSheet(book), 1);
            row.getCell(6).setCellFormula("50+50");
            var response = service.preview(upload(book));
            assertEquals(1, response.invalidRows());
            assertTrue(response.rows().get(0).errors().stream()
                    .anyMatch(s -> s.contains("Quantidade: fórmulas não são aceitas")));
        }
    }

    @Test
    void warnsOnMismatchedTotalWithoutSilentlyChangingAccountingValues() throws Exception {
        try (var book = new XSSFWorkbook()) {
            var row = validRow(initializedSheet(book), 1);
            row.getCell(8).setCellValue(4999);
            var response = service.preview(upload(book));
            assertEquals(1, response.validRows());
            assertEquals(0, response.invalidRows());
            assertEquals(1, response.rows().get(0).warnings().size());
            assertEquals(0, new BigDecimal("4999.0").compareTo(response.rows().get(0).totalValue()));
        }
    }

    @Test
    void supportsBrazilianCurrencyTextAndReportsInvalidNumericText() throws Exception {
        try (var book = new XSSFWorkbook()) {
            var sheet = initializedSheet(book);
            var first = validRow(sheet, 1);
            first.getCell(6).setCellValue("100");
            first.getCell(7).setCellValue("R$ 49,00");
            first.getCell(8).setCellValue("4.900,00");
            var second = validRow(sheet, 2);
            second.getCell(7).setCellValue("abc");
            var response = service.preview(upload(book));
            assertEquals(1, response.validRows());
            assertEquals(1, response.invalidRows());
            assertEquals(0, new BigDecimal("4900").compareTo(response.rows().get(0).totalValue()));
            assertTrue(response.rows().get(1).errors().stream().anyMatch(s -> s.contains("Preço")));
        }
    }
}
