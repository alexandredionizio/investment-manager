package com.investmanager.api.b3import;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class B3IncomePreviewServiceTest {
    private final B3IncomePreviewService service = new B3IncomePreviewService();

    private MockMultipartFile excel(String movement, String direction, String date, String amount) throws Exception {
        try (var book = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("Movimentação");
            var header = sheet.createRow(0);
            String[] columns = {"Entrada/Saída", "Data", "Movimentação", "Produto", "Instituição",
                    "Quantidade", "Preço unitário", "Valor da Operação"};
            for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue(direction);
            row.createCell(1).setCellValue(date);
            row.createCell(2).setCellValue(movement);
            row.createCell(3).setCellValue("PETR4");
            row.createCell(4).setCellValue("CORRETORA");
            row.createCell(5).setCellValue(100);
            row.createCell(6).setCellValue("R$ 0,50");
            row.createCell(7).setCellValue(amount);
            book.write(output);
            return new MockMultipartFile("file", "movimentacao.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    @Test
    void previewsDividendWithoutInventingBaseDateOrSaving() throws Exception {
        var result = service.preview(excel("Dividendo", "Credito", "10/11/2026", "R$ 50,00"));
        assertEquals(1, result.totalRows());
        assertEquals(1, result.incomeRows());
        assertEquals(0, result.skippedRows());
        var row = result.rows().get(0);
        assertEquals("DIVIDEND", row.type());
        assertEquals(new BigDecimal("50.00"), row.creditedAmount());
        assertEquals(2026, row.paymentDate().getYear());
        assertTrue(row.warnings().stream().anyMatch(s -> s.contains("Valor creditado")));
        assertTrue(row.errors().isEmpty());
    }

    @Test
    void identifiesJcpAndDoesNotTreatGrossAsCredited() throws Exception {
        var result = service.preview(excel("Juros sobre Capital Próprio", "Credito", "10/11/2026", "R$ 42,50"));
        assertEquals("JCP", result.rows().get(0).type());
        assertEquals(new BigDecimal("42.50"), result.rows().get(0).creditedAmount());
        assertTrue(result.rows().get(0).warnings().stream().anyMatch(s -> s.contains("difere")));
    }

    @Test
    void ignoresUnrelatedMovementAndFlagsInvalidDate() throws Exception {
        assertEquals(1, service.preview(excel("Transferência", "Credito", "10/11/2026", "R$ 50,00")).skippedRows());
        var invalid = service.preview(excel("Rendimento", "Debito", "31/02/2026", "R$ 50,00"));
        assertEquals("FII_INCOME", invalid.rows().get(0).type());
        assertEquals(2, invalid.rows().get(0).errors().size());
    }

    @Test
    void rejectsUnexpectedSheetInsteadOfGuessingColumns() throws Exception {
        var invalid = new MockMultipartFile("file", "dados.txt", "text/plain", "a".getBytes());
        assertThrows(IllegalArgumentException.class, () -> service.preview(invalid));
    }
}
