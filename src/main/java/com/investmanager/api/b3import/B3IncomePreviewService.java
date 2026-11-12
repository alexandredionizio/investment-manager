package com.investmanager.api.b3import;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;

@Service
public class B3IncomePreviewService {
    private static final String[] HEADERS = {
            "Entrada/Saída", "Data", "Movimentação", "Produto", "Instituição",
            "Quantidade", "Preço unitário", "Valor da Operação"
    };
    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DataFormatter DISPLAY = new DataFormatter(Locale.forLanguageTag("pt-BR"));
    private static final int MAX_ROWS = 10_000;
    private static final long MAX_BYTES = 10L * 1024 * 1024;

    public B3IncomePreviewResponse preview(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Envie um arquivo Excel da aba Movimentação.");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx"))
            throw new IllegalArgumentException("Somente arquivos .xlsx são aceitos.");
        if (file.getSize() > MAX_BYTES) throw new IllegalArgumentException("O arquivo excede 10 MB.");
        try (var in = file.getInputStream(); var book = WorkbookFactory.create(in)) {
            if (book.getNumberOfSheets() == 0) throw new IllegalArgumentException("Planilha sem abas.");
            var sheet = book.getSheetAt(0);
            var header = sheet.getRow(0);
            if (header == null) throw new IllegalArgumentException("Cabeçalho da aba Movimentação ausente.");
            for (int col = 0; col < HEADERS.length; col++) {
                if (!normalize(value(header.getCell(col))).equals(normalize(HEADERS[col])))
                    throw new IllegalArgumentException("Cabeçalho B3 inválido na coluna " + (col + 1) + ": esperado '" + HEADERS[col] + "'.");
            }
            if (sheet.getLastRowNum() > MAX_ROWS)
                throw new IllegalArgumentException("Máximo permitido: " + MAX_ROWS + " linhas.");
            var incomes = new ArrayList<B3IncomePreviewRow>();
            var skippedDetails = new ArrayList<B3IncomePreviewResponse.SkippedMovement>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                var row = sheet.getRow(index);
                if (row == null || empty(row)) continue;
                String movement = value(row.getCell(2)).trim();
                String type = classify(movement);
                if (type == null) {
                    skippedDetails.add(new B3IncomePreviewResponse.SkippedMovement(
                            index + 1, movement.isBlank() ? "(não informada)" : movement,
                            "Movimentação não reconhecida como dividendo, JCP ou rendimento; nenhum lançamento automático foi realizado."));
                    continue;
                }
                var errors = new ArrayList<String>();
                var warnings = new ArrayList<String>();
                if (!normalize(value(row.getCell(0))).equals("credito"))
                    errors.add("A movimentação não é um crédito.");
                LocalDate date = date(row.getCell(1), errors);
                String product = value(row.getCell(3)).trim();
                String institution = value(row.getCell(4)).trim();
                if (product.isEmpty()) errors.add("Produto obrigatório.");
                if (institution.isEmpty()) warnings.add("Instituição não informada.");
                BigDecimal quantity = amount(row.getCell(5), "Quantidade", errors);
                BigDecimal unit = amount(row.getCell(6), "Preço unitário", errors);
                BigDecimal credited = amount(row.getCell(7), "Valor da Operação", errors);
                if (quantity != null && unit != null && credited != null &&
                        quantity.multiply(unit).subtract(credited).abs().compareTo(new BigDecimal("0.02")) > 0)
                    warnings.add("Valor creditado difere de quantidade × preço: conferir tributos, arredondamentos e extrato.");
                warnings.add("Valor creditado será preservado conforme o extrato B3.");
                incomes.add(new B3IncomePreviewRow(index + 1, date, movement, product, institution,
                        type, quantity, unit, credited, List.copyOf(errors), List.copyOf(warnings)));
            }
            return new B3IncomePreviewResponse(file.getOriginalFilename(), incomes.size() + skippedDetails.size(),
                    incomes.size(), skippedDetails.size(), List.copyOf(incomes), List.copyOf(skippedDetails));
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Não foi possível ler o Excel B3.", exception);
        }
    }

    private static String classify(String value) {
        String norm = normalize(value);
        if (norm.equals("dividendo") || norm.equals("dividendos")) return "DIVIDEND";
        if (norm.equals("juros sobre capital proprio") || norm.equals("jcp")) return "JCP";
        if (norm.equals("rendimento") || norm.equals("rendimentos")) return "FII_INCOME";
        return null;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static boolean empty(Row row) {
        for (int i = 0; i < HEADERS.length; i++) if (!value(row.getCell(i)).isBlank()) return false;
        return true;
    }

    private static String value(Cell cell) {
        if (cell == null || cell.getCellType() == CellType.BLANK) return "";
        if (cell.getCellType() == CellType.FORMULA) return ""; // Nunca executa fórmulas externas.
        return DISPLAY.formatCellValue(cell);
    }

    private static LocalDate date(Cell cell, List<String> errors) {
        if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            try { return cell.getLocalDateTimeCellValue().toLocalDate(); }
            catch (RuntimeException ignored) { /* report below */ }
        }
        if (cell != null && cell.getCellType() == CellType.FORMULA) {
            errors.add("Data: fórmulas não são aceitas."); return null;
        }
        try { return LocalDate.parse(value(cell).trim(), BR_DATE); }
        catch (DateTimeParseException exception) {
            errors.add("Data inválida; esperado dd/MM/aaaa."); return null;
        }
    }

    private static BigDecimal amount(Cell cell, String field, List<String> errors) {
        if (cell == null || cell.getCellType() == CellType.BLANK || cell.getCellType() == CellType.FORMULA) {
            errors.add(field + " ausente ou contém fórmula."); return null;
        }
        try {
            BigDecimal number;
            if (cell.getCellType() == CellType.NUMERIC) number = BigDecimal.valueOf(cell.getNumericCellValue());
            else {
                String val = value(cell).replace("R$", "").replace("\u00a0", "").replace(" ", "").trim();
                if (!val.matches("[0-9]+(?:\\.[0-9]{3})*(?:,[0-9]+)?")) throw new NumberFormatException();
                number = new BigDecimal(val.replace(".", "").replace(',', '.'));
            }
            if (number.signum() <= 0) throw new NumberFormatException();
            return number;
        } catch (NumberFormatException exception) {
            errors.add(field + " precisa ser um número positivo."); return null;
        }
    }
}
