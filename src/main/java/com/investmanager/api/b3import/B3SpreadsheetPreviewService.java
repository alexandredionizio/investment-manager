package com.investmanager.api.b3import;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class B3SpreadsheetPreviewService {
    public static final long MAX_BYTES = 2L * 1024 * 1024;
    public static final int MAX_ROWS = 10_000;

    private static final List<String> HEADERS = List.of(
            "Data do Negócio", "Tipo de Movimentação", "Mercado", "Prazo/Vencimento",
            "Instituição", "Código de Negociação", "Quantidade", "Preço", "Valor"
    );
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(java.time.format.ResolverStyle.STRICT);
    private static final DataFormatter DISPLAY = new DataFormatter(Locale.forLanguageTag("pt-BR"));

    public B3PreviewResponse preview(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) throw new IllegalArgumentException("Envie uma planilha Excel não vazia.");
        String name = upload.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new IllegalArgumentException("Somente arquivos .xlsx são aceitos.");
        if (upload.getSize() > MAX_BYTES) throw new IllegalArgumentException("O arquivo excede 2 MB.");

        try (var input = upload.getInputStream(); var workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() < 1) throw new IllegalArgumentException("Planilha sem abas.");
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            if (header == null) throw new IllegalArgumentException("Cabeçalho B3 ausente.");
            for (int col = 0; col < HEADERS.size(); col++) {
                String actual = display(header.getCell(col)).trim();
                if (!HEADERS.get(col).equals(actual))
                    throw new IllegalArgumentException("Cabeçalho inválido na coluna " + (col + 1)
                            + ": esperado '" + HEADERS.get(col) + "'.");
            }
            if (sheet.getLastRowNum() > MAX_ROWS)
                throw new IllegalArgumentException("Máximo permitido: " + MAX_ROWS + " linhas de dados.");

            List<B3PreviewRow> result = new ArrayList<>();
            for (int n = 1; n <= sheet.getLastRowNum(); n++) {
                Row row = sheet.getRow(n);
                if (row == null || blank(row)) continue;
                List<String> errors = new ArrayList<>();
                List<String> warnings = new ArrayList<>();
                LocalDate date = parseDate(row.getCell(0), errors);
                String type = required(row.getCell(1), "Tipo de Movimentação", errors);
                if (!type.isBlank() && !type.equalsIgnoreCase("Compra") && !type.equalsIgnoreCase("Venda"))
                    errors.add("Tipo de movimentação deve ser Compra ou Venda.");
                String market = required(row.getCell(2), "Mercado", errors);
                String expiry = display(row.getCell(3)).trim();
                String institution = required(row.getCell(4), "Instituição", errors);
                String ticker = required(row.getCell(5), "Código de Negociação", errors).toUpperCase(Locale.ROOT);
                BigDecimal qty = parsePositive(row.getCell(6), "Quantidade", errors);
                BigDecimal price = parsePositive(row.getCell(7), "Preço", errors);
                BigDecimal value = parsePositive(row.getCell(8), "Valor", errors);
                if (qty != null && price != null && value != null) {
                    BigDecimal difference = qty.multiply(price).subtract(value).abs();
                    if (difference.compareTo(new BigDecimal("0.01")) > 0)
                        warnings.add("Valor diferente de quantidade × preço; conferir antes de importar.");
                }
                result.add(new B3PreviewRow(n + 1, date, type, market, expiry, institution,
                        ticker, qty, price, value, List.copyOf(errors), List.copyOf(warnings)));
            }
            long valid = result.stream().filter(B3PreviewRow::valid).count();
            return new B3PreviewResponse(name, result.size(), (int) valid,
                    result.size() - (int) valid, List.copyOf(result));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("Arquivo Excel inválido ou não pôde ser lido.", e);
        }
    }

    private static boolean blank(Row row) {
        for (int i = 0; i < HEADERS.size(); i++) if (!display(row.getCell(i)).isBlank()) return false;
        return true;
    }

    private static String display(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.FORMULA) return ""; // Não executamos fórmulas de arquivos externos.
        return DISPLAY.formatCellValue(cell);
    }

    private static String required(Cell cell, String field, List<String> errors) {
        if (cell != null && cell.getCellType() == CellType.FORMULA) {
            errors.add(field + ": fórmulas não são aceitas."); return "";
        }
        String value = display(cell).trim();
        if (value.isEmpty()) errors.add(field + " obrigatório.");
        return value;
    }

    private static LocalDate parseDate(Cell cell, List<String> errors) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            try { return cell.getLocalDateTimeCellValue().toLocalDate(); }
            catch (RuntimeException ignored) { /* gera erro de data abaixo */ }
        }
        if (cell != null && cell.getCellType() == CellType.FORMULA) {
            errors.add("Data: fórmulas não são aceitas."); return null;
        }
        try { return LocalDate.parse(display(cell).trim(), BR_DATE); }
        catch (DateTimeParseException e) { errors.add("Data inválida; use dd/MM/aaaa."); return null; }
    }

    private static BigDecimal parsePositive(Cell cell, String field, List<String> errors) {
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            errors.add(field + " obrigatório."); return null;
        }
        if (cell.getCellType() == CellType.FORMULA) {
            errors.add(field + ": fórmulas não são aceitas."); return null;
        }
        try {
            BigDecimal number;
            if (cell.getCellType() == CellType.NUMERIC) {
                number = BigDecimal.valueOf(cell.getNumericCellValue());
            } else {
                String text = display(cell).replace("R$", "").replace("\u00a0", "")
                        .replace(" ", "").trim();
                if (text.isEmpty()) throw new NumberFormatException();
                // Texto em pt-BR: separador de milhares '.' e decimal ','.
                if (!text.matches("[0-9]+(?:\\.[0-9]{3})*(?:,[0-9]+)?"))
                    throw new NumberFormatException();
                number = new BigDecimal(text.replace(".", "").replace(',', '.'));
            }
            if (number.signum() <= 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException e) {
            errors.add(field + " deve ser numérico e maior que zero."); return null;
        }
    }
}
