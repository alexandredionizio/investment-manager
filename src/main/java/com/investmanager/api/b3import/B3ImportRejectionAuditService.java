package com.investmanager.api.b3import;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Audita SOMENTE tentativas explicitamente confirmadas e rejeitadas.
 * A prévia e a conciliação continuam read-only. Esta transação independente
 * não pode registrar transações financeiras nem alterar um lote aprovado.
 */
@Service
public class B3ImportRejectionAuditService {
    public record RejectedRow(int excelRow, String reason) {}
    public record RejectedAttempt(long id, String fileName, java.time.OffsetDateTime rejectedAt,
                                  String reason, List<RejectedRow> rows) {}

    private final JdbcTemplate jdbc;
    private final B3SpreadsheetPreviewService previews;
    private final B3ReconciliationService reconciliation;

    public B3ImportRejectionAuditService(JdbcTemplate jdbc, B3SpreadsheetPreviewService previews,
                                         B3ReconciliationService reconciliation) {
        this.jdbc = jdbc;
        this.previews = previews;
        this.reconciliation = reconciliation;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(MultipartFile file, Long portfolioId, Long userId, String reason) {
        String filename = file == null ? "arquivo desconhecido" : file.getOriginalFilename();
        if (filename == null || filename.isBlank()) filename = "arquivo sem nome";
        if (filename.length() > 255) filename = filename.substring(0, 255);
        String sha = sha256(file);
        Long attempt = jdbc.queryForObject("""
                INSERT INTO b3_import_rejected_attempts
                    (portfolio_id, user_id, source_filename, source_sha256, reason)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """, Long.class, portfolioId, userId, filename, sha, reason);
        if (attempt == null) throw new IllegalStateException("Tentativa rejeitada sem identificador.");
        // Apenas problemas efetivamente apresentados pelo parser são vinculados a uma linha.
        // Uma falha de regra de negócio não é atribuída artificialmente a todas as linhas.
        Map<Integer, List<String>> reasons = new TreeMap<>();
        // Um conflito detectado durante o commit pode desaparecer ao reconstruir a
        // conciliação após o rollback. O número da linha consta da própria recusa;
        // preservá-lo evita perder o vínculo auditável com a planilha original.
        // Apenas razões emitidas no formato conhecido "Linha N ..." são associadas.
        if (reason != null) {
            var line = java.util.regex.Pattern.compile("^Linha (\\d+)\\b").matcher(reason);
            if (line.find()) {
                try {
                    int excelRow = Integer.parseInt(line.group(1));
                    if (excelRow > 0) reasons.computeIfAbsent(excelRow, ignored -> new ArrayList<>()).add(reason);
                } catch (NumberFormatException ignored) {
                    // Número inválido: manter a razão global, sem inventar uma linha.
                }
            }
        }
        try {
            B3PreviewResponse preview = previews.preview(file);
            for (B3PreviewRow row : preview.rows()) {
                if (row.errors().isEmpty() && row.warnings().isEmpty()) continue;
                reasons.computeIfAbsent(row.excelRow(), ignored -> new ArrayList<>())
                        .addAll(java.util.stream.Stream.concat(row.errors().stream(), row.warnings().stream()).toList());
            }
            // A conciliação é read-only; registra os motivos efetivos de REVIEW/BLOCKED
            // quando o serviço consegue reconstruí-los após o rollback da tentativa.
            try {
                B3ReconciliationResponse checked = reconciliation.reconcile(file, portfolioId, userId);
                for (B3ReconciliationResponse.Row row : checked.rows()) {
                    if ("READY".equals(row.status()) || row.messages().isEmpty()) continue;
                    reasons.computeIfAbsent(row.excelRow(), ignored -> new ArrayList<>()).addAll(row.messages());
                }
            } catch (RuntimeException ignored) {
                // A causa global já está persistida, mesmo sem conciliação por linha.
            }
        } catch (RuntimeException ignored) {
            // Cabeçalho/arquivo inválido não oferece números de linha confiáveis.
        }
        List<RejectedRow> rows = reasons.entrySet().stream()
                .map(entry -> new RejectedRow(entry.getKey(),
                        String.join("; ", entry.getValue().stream().distinct().toList())))
                .toList();
        for (RejectedRow row : rows) {
            jdbc.update("""
                    INSERT INTO b3_import_rejected_rows(attempt_id, excel_row, reason)
                    VALUES (?, ?, ?)
                    """, attempt, row.excelRow(), row.reason());
        }
    }

    @Transactional(readOnly = true)
    public List<RejectedAttempt> find(Long portfolioId, Long userId) {
        var attempts = jdbc.query("""
                SELECT id, source_filename, rejected_at, reason
                FROM b3_import_rejected_attempts
                WHERE portfolio_id = ? AND user_id = ?
                ORDER BY rejected_at DESC, id DESC LIMIT 50
                """, (rs, i) -> new RejectedAttempt(rs.getLong("id"), rs.getString("source_filename"),
                rs.getObject("rejected_at", java.time.OffsetDateTime.class),
                rs.getString("reason"), List.of()), portfolioId, userId);
        List<RejectedAttempt> result = new ArrayList<>();
        for (RejectedAttempt attempt : attempts) {
            var rows = jdbc.query("""
                    SELECT excel_row, reason FROM b3_import_rejected_rows
                    WHERE attempt_id = ? ORDER BY excel_row LIMIT 100
                    """, (rs, i) -> new RejectedRow(rs.getInt("excel_row"), rs.getString("reason")), attempt.id());
            result.add(new RejectedAttempt(attempt.id(), attempt.fileName(), attempt.rejectedAt(), attempt.reason(), rows));
        }
        return List.copyOf(result);
    }

    private String sha256(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;
        try (InputStream input = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ignored) {
            // Não impedir a auditoria quando a leitura da origem estiver indisponível.
            return null;
        }
    }
}
