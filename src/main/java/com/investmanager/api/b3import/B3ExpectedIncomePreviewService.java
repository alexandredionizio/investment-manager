package com.investmanager.api.b3import;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Somente prévia de Eventos B3. O arquivo não comprova o crédito do provento. */
@Service
public class B3ExpectedIncomePreviewService {
    private static final String[] HEADERS = {"Produto", "Tipo", "Tipo de Evento", "Previsão de pagamento",
            "Instituição", "Conta", "Quantidade", "Preço unitário", "Valor líquido"};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DataFormatter FORMAT = new DataFormatter(Locale.forLanguageTag("pt-BR"));
    private static final int MAX_ROWS = 10000;

    public B3ExpectedIncomePreviewResponse preview(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Envie um arquivo Excel de Eventos da B3.");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new IllegalArgumentException("Somente arquivos .xlsx são aceitos.");
        if (file.getSize() > 10L * 1024 * 1024) throw new IllegalArgumentException("O arquivo excede 10 MB.");
        try (var input = file.getInputStream(); var book = WorkbookFactory.create(input)) {
            if (book.getNumberOfSheets() == 0) throw new IllegalArgumentException("Planilha sem abas.");
            var sheet = book.getSheetAt(0);
            var headers = sheet.getRow(0);
            if (headers == null) throw new IllegalArgumentException("Cabeçalho da aba Eventos ausente.");
            for (int i = 0; i < HEADERS.length; i++) {
                if (!norm(value(headers.getCell(i))).equals(norm(HEADERS[i])))
                    throw new IllegalArgumentException("Cabeçalho Eventos inválido na coluna " + (i+1) + ": esperado '" + HEADERS[i] + "'.");
            }
            if (sheet.getLastRowNum() > MAX_ROWS) throw new IllegalArgumentException("Máximo permitido: " + MAX_ROWS + " linhas.");
            var rows = new ArrayList<B3ExpectedIncomePreviewRow>();
            int skipped = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                var line = sheet.getRow(i);
                if (line == null || empty(line)) continue;
                String product = value(line.getCell(0)).trim();
                String generalType = value(line.getCell(1)).trim();
                String event = value(line.getCell(2)).trim();
                String type = classify(event);
                if (type == null) type = classify(generalType);
                if (type == null) { skipped++; continue; }
                var errors = new ArrayList<String>();
                var warnings = new ArrayList<String>();
                if (product.isBlank()) errors.add("Produto obrigatório.");
                LocalDate date = date(line.getCell(3), errors);
                String institution = value(line.getCell(4)).trim();
                String account = value(line.getCell(5)).trim();
                if (institution.isBlank()) warnings.add("Instituição não informada.");
                BigDecimal quantity = amount(line.getCell(6), "Quantidade", errors);
                BigDecimal unit = amount(line.getCell(7), "Preço unitário", errors);
                BigDecimal net = amount(line.getCell(8), "Valor líquido", errors);
                warnings.add("Evento previsto: não representa crédito recebido. Data-base não informada.");
                rows.add(new B3ExpectedIncomePreviewRow(i+1, product, event, type, date,
                        institution, account, quantity, unit, net, List.copyOf(errors), List.copyOf(warnings)));
            }
            return new B3ExpectedIncomePreviewResponse(file.getOriginalFilename(), rows.size()+skipped,
                    rows.size(), skipped, List.copyOf(rows));
        } catch (IllegalArgumentException ex) { throw ex; }
        catch (IOException | RuntimeException ex) { throw new IllegalArgumentException("Não foi possível ler o arquivo Eventos da B3.", ex); }
    }

    private static boolean empty(Row row) {
        for (int j=0; j<HEADERS.length; j++) if (!value(row.getCell(j)).isBlank()) return false;
        return true;
    }
    private static String classify(String original) {
        var s=norm(original);
        if (s.equals("dividendo") || s.equals("dividendos")) return "DIVIDEND";
        if (s.equals("jcp") || s.equals("juros sobre capital proprio")) return "JCP";
        if (s.equals("rendimento") || s.equals("rendimentos")) return "FII_INCOME";
        return null;
    }
    private static String norm(String s) {
        return Normalizer.normalize(s == null ? "" : s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
    private static String value(Cell c) {
        if (c == null || c.getCellType() == CellType.BLANK || c.getCellType() == CellType.FORMULA) return "";
        return FORMAT.formatCellValue(c);
    }
    private static LocalDate date(Cell cell, List<String> errors) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            try { return cell.getLocalDateTimeCellValue().toLocalDate(); }
            catch (RuntimeException ignored) { /* below */ }
        }
        if (cell != null && cell.getCellType() == CellType.FORMULA) {
            errors.add("Previsão de pagamento: fórmula não é aceita."); return null;
        }
        try { return LocalDate.parse(value(cell).trim(), DATE); }
        catch (DateTimeParseException ex) { errors.add("Previsão de pagamento inválida; esperado dd/MM/aaaa."); return null; }
    }
    private static BigDecimal amount(Cell c, String label, List<String> errors) {
        if (c == null || c.getCellType() == CellType.BLANK || c.getCellType() == CellType.FORMULA) {
            errors.add(label + " ausente ou contém fórmula."); return null;
        }
        try {
            BigDecimal number;
            if (c.getCellType() == CellType.NUMERIC) number = BigDecimal.valueOf(c.getNumericCellValue());
            else {
                String v = value(c).replace("R$", "").replace("\u00a0", "").replace(" ", "").trim();
                if (!v.matches("[0-9]+(?:\\.[0-9]{3})*(?:,[0-9]+)?")) throw new NumberFormatException();
                number = new BigDecimal(v.replace(".", "").replace(',', '.'));
            }
            if (number.signum() <= 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException ex) { errors.add(label + " precisa ser positivo."); return null; }
    }
}
