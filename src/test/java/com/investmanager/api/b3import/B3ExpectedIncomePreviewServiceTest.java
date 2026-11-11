package com.investmanager.api.b3import;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class B3ExpectedIncomePreviewServiceTest {
    private final B3ExpectedIncomePreviewService service = new B3ExpectedIncomePreviewService();

    private MockMultipartFile excel(String event, String date, String amount) throws Exception {
        try (var book = new XSSFWorkbook(); var out = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("Eventos");
            String[] headers = {"Produto", "Tipo", "Tipo de Evento", "Previsão de pagamento", "Instituição", "Conta", "Quantidade", "Preço unitário", "Valor líquido"};
            var header = sheet.createRow(0);
            for (int i=0;i<headers.length;i++) header.createCell(i).setCellValue(headers[i]);
            var row = sheet.createRow(1);
            row.createCell(0).setCellValue("PETR4"); row.createCell(1).setCellValue("Provento");
            row.createCell(2).setCellValue(event); row.createCell(3).setCellValue(date);
            row.createCell(4).setCellValue("CORRETORA"); row.createCell(5).setCellValue("Conta 1");
            row.createCell(6).setCellValue(100); row.createCell(7).setCellValue("R$ 0,50");
            row.createCell(8).setCellValue(amount);
            book.write(out);
            return new MockMultipartFile("file", "eventos.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    @Test void previewsExpectedPaymentWithoutClaimingReceipt() throws Exception {
        var result=service.preview(excel("Dividendo", "01/12/2026", "R$ 40,00"));
        assertEquals(1, result.recognizedRows()); assertEquals(0,result.skippedRows());
        var row=result.rows().get(0);
        assertEquals("DIVIDEND",row.type()); assertEquals(new BigDecimal("40.00"),row.expectedNetAmount());
        assertEquals(12,row.expectedPaymentDate().getMonthValue());
        assertTrue(row.warnings().stream().anyMatch(s->s.contains("não representa crédito")));
    }
    @Test void skipsNonIncomeEventsAndRejectsImpossibleDates() throws Exception {
        assertEquals(1,service.preview(excel("Direito de Subscrição","01/12/2026","R$ 40,00")).skippedRows());
        assertFalse(service.preview(excel("Juros sobre Capital Próprio","31/02/2026","R$ 40,00")).rows().get(0).errors().isEmpty());
    }
    @Test void refusesOtherReportInsteadOfGuessing() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> service.preview(new MockMultipartFile("file", "movimentacoes.txt", "text/plain", new byte[]{1})));
    }
}
