package com.investmanager.api.b3import;

import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.Transaction;
import com.investmanager.api.transaction.TransactionType;
import com.investmanager.api.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pré-conciliação estritamente read-only. Não cria operações, não reserva saldos
 * e não garante elegibilidade para gravação futura (que exigirá nova validação atômica).
 * Operações existentes do mesmo dia são processadas antes das linhas importadas;
 * como a B3 não fornece horário/ID de negócio, essa ordenação é apenas hipotética.
 */
@Service
public class B3ReconciliationService {
    private final B3SpreadsheetPreviewService previews;
    private final B3MatchingService matching;
    private final PortfolioRepository portfolios;
    private final TransactionRepository transactions;

    public B3ReconciliationService(B3SpreadsheetPreviewService previews, B3MatchingService matching,
                                   PortfolioRepository portfolios, TransactionRepository transactions) {
        this.previews = previews;
        this.matching = matching;
        this.portfolios = portfolios;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public B3ReconciliationResponse reconcile(MultipartFile file, Long portfolioId, Long userId) {
        if (portfolioId == null || userId == null ||
                portfolios.findByIdAndUserId(portfolioId, userId).isEmpty()) {
            throw new IllegalArgumentException("Carteira inexistente ou não pertence ao usuário autenticado.");
        }
        B3PreviewResponse preview = previews.preview(file);
        B3MatchResponse matched = matching.match(file);
        if (preview.totalRows() != matched.totalRows())
            throw new IllegalStateException("Prévia e correspondência divergentes. Reenvie a planilha.");
        Map<Integer, B3MatchResponse.Row> byLine = matched.rows().stream().collect(Collectors.toMap(
                B3MatchResponse.Row::excelRow, row -> row, (a,b) -> { throw new IllegalStateException("Linha duplicada na correspondência."); }));
        List<Transaction> history = transactions.findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(portfolioId, userId);
        List<Event> events = new ArrayList<>();
        Set<Key> existingKeys = new HashSet<>();
        Map<AssetDay, Set<TransactionType>> sameDayTypes = new HashMap<>();
        for (Transaction tx : history) {
            if (tx.getAsset() == null || tx.getAsset().getId() == null || tx.getTransactionDate() == null ||
                    tx.getType() == null || tx.getQuantity() == null) {
                throw new IllegalStateException("Histórico contém uma transação incompleta; conciliação indisponível.");
            }
            Long id = tx.getAsset().getId();
            events.add(new Event(tx.getTransactionDate(), -1, tx.getId() == null ? 0 : tx.getId().intValue(),
                    id, tx.getType(), tx.getQuantity(), null));
            sameDayTypes.computeIfAbsent(new AssetDay(id, tx.getTransactionDate()), k -> new HashSet<>()).add(tx.getType());
            existingKeys.add(new Key(tx.getTransactionDate(), id,
                    tx.getBroker() == null ? null : tx.getBroker().getId(),
                    tx.getType(), tx.getQuantity(), tx.getUnitPrice()));
        }
        Map<Integer, MutableRow> data = new HashMap<>();
        Map<Key, List<Integer>> importedKeys = new HashMap<>();
        for (B3PreviewRow row : preview.rows()) {
            B3MatchResponse.Row match = byLine.get(row.excelRow());
            if (match == null) throw new IllegalStateException("Correspondência ausente na linha " + row.excelRow());
            MutableRow result = new MutableRow(row, match);
            data.put(row.excelRow(), result);
            if (!row.valid()) {
                result.block(row.errors());
                continue;
            }
            if (!"MATCHED".equals(match.assetStatus()) || !"MATCHED".equals(match.brokerStatus()) ||
                    match.assetId() == null || match.brokerId() == null) {
                result.block(List.of("É necessário identificar ativo e corretora antes da conciliação."));
                continue;
            }
            if (row.tradeDate() == null || row.quantity() == null || row.unitPrice() == null || row.totalValue() == null) {
                result.block(List.of("Data, quantidade, preço ou valor ausente."));
                continue;
            }
            TransactionType type = type(row.movementType());
            if (type == null) { result.block(List.of("Movimentação não suportada.")); continue; }
            Key key = new Key(row.tradeDate(), match.assetId(), match.brokerId(), type, row.quantity(), row.unitPrice());
            if (existingKeys.contains(key))
                result.review("Possível duplicata: operação equivalente já existe nesta carteira (não é identificação conclusiva).");
            importedKeys.computeIfAbsent(key, k -> new ArrayList<>()).add(row.excelRow());
            if (!row.warnings().isEmpty()) result.review(String.join(" ", row.warnings()));
            if (!row.tradeDate().isBefore(LocalDate.now().plusDays(1)))
                result.review("Data futura: conferir antes de importar.");
            events.add(new Event(row.tradeDate(), 0, row.excelRow(), match.assetId(), type, row.quantity(), result));
        }
        for (List<Integer> lines : importedKeys.values()) {
            if (lines.size() > 1) for (Integer line : lines)
                data.get(line).review("Possível duplicata dentro da planilha (linhas " + lines + ").");
        }
        // A B3 contém apenas datas, sem identificador/horário de execução.
        // Histórico existente e arquivo com operações opostas no mesmo dia exigem revisão.
        Map<AssetDay, Set<TransactionType>> importedTypes = new HashMap<>();
        for (Event event : events) if (event.row() != null)
            importedTypes.computeIfAbsent(new AssetDay(event.assetId(), event.date()), k -> new HashSet<>()).add(event.type());
        for (Event event : events) {
            if (event.row() == null) continue;
            AssetDay day = new AssetDay(event.assetId(), event.date());
            Set<TransactionType> oldTypes = sameDayTypes.getOrDefault(day, Set.of());
            Set<TransactionType> newTypes = importedTypes.getOrDefault(day, Set.of());
            if ((!oldTypes.isEmpty() && (event.type() == TransactionType.SELL || oldTypes.contains(TransactionType.SELL))) ||
                    newTypes.size() > 1) {
                event.row().review("Ordem intradiária não verificável; há operações relacionadas na mesma data.");
            }
        }
        events.sort(Comparator.comparing(Event::date)
                .thenComparingInt(Event::sourceOrder)
                .thenComparingInt(Event::sequence));
        Map<Long, BigDecimal> balances = new HashMap<>();
        Set<Long> historicallyInvalid = new HashSet<>();
        for (Event event : events) {
            BigDecimal before = balances.getOrDefault(event.assetId(), BigDecimal.ZERO);
            BigDecimal after = event.type() == TransactionType.BUY
                    ? before.add(event.quantity()) : before.subtract(event.quantity());
            if (event.row() == null) {
                if (after.signum() < 0) historicallyInvalid.add(event.assetId());
            } else {
                if (historicallyInvalid.contains(event.assetId()))
                    event.row().block(List.of("Histórico existente apresenta saldo negativo anterior; análise indisponível."));
                if (after.signum() < 0)
                    event.row().block(List.of("Venda excede saldo cronológico simulado da carteira; verificar compras anteriores."));
            }
            balances.put(event.assetId(), after);
        }
        List<B3ReconciliationResponse.Row> result = data.values().stream()
                .sorted(Comparator.comparingInt(r -> r.preview.excelRow()))
                .map(MutableRow::toResponse).toList();
        return new B3ReconciliationResponse(preview.fileName(), portfolioId, result.size(),
                (int) result.stream().filter(r -> r.status().equals("READY")).count(),
                (int) result.stream().filter(r -> r.status().equals("REVIEW")).count(),
                (int) result.stream().filter(r -> r.status().equals("BLOCKED")).count(), result);
    }

    private static TransactionType type(String movement) {
        if (movement == null) return null;
        if (movement.equalsIgnoreCase("Compra")) return TransactionType.BUY;
        if (movement.equalsIgnoreCase("Venda")) return TransactionType.SELL;
        return null;
    }
    private record AssetDay(Long id, LocalDate date) {}
    private record Key(LocalDate date, Long assetId, Long brokerId, TransactionType type,
                       BigDecimal qty, BigDecimal price) {
        private Key { qty = qty == null ? null : qty.stripTrailingZeros(); price = price == null ? null : price.stripTrailingZeros(); }
    }
    private record Event(LocalDate date, int sourceOrder, int sequence, Long assetId,
                         TransactionType type, BigDecimal quantity, MutableRow row) {}
    private static final class MutableRow {
        final B3PreviewRow preview;
        final B3MatchResponse.Row match;
        final List<String> messages = new ArrayList<>();
        String status = "READY";
        MutableRow(B3PreviewRow preview, B3MatchResponse.Row match) {
            this.preview = preview; this.match = match;
        }
        void review(String message) {
            if (!"BLOCKED".equals(status)) status = "REVIEW";
            messages.add(message);
        }
        void block(List<String> problems) { status = "BLOCKED"; messages.addAll(problems); }
        B3ReconciliationResponse.Row toResponse() {
            return new B3ReconciliationResponse.Row(preview.excelRow(), preview.tradeDate(), preview.ticker(),
                    preview.institution(), preview.movementType(), match.assetId(), match.brokerId(),
                    preview.quantity(), preview.unitPrice(), status, List.copyOf(messages));
        }
    }
}
