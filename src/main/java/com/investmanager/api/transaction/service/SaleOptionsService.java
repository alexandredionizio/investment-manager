package com.investmanager.api.transaction.service;
import com.investmanager.api.asset.AssetType;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.transaction.*;
import com.investmanager.api.transaction.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@Service
public class SaleOptionsService {
    public record Option(Long assetId, String ticker, String name, AssetType type, BigDecimal quantityAtDate, BigDecimal chronologicalLimit) {}
    private final PortfolioRepository portfolios;
    private final TransactionRepository transactions;
    public SaleOptionsService(PortfolioRepository portfolios, TransactionRepository transactions) { this.portfolios = portfolios; this.transactions = transactions; }
    @Transactional(readOnly = true)
    public List<Option> find(Long portfolioId, Long userId, LocalDate date) {
        portfolios.findByIdAndUserId(portfolioId, userId).orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
        Map<Long, List<Transaction>> histories = new LinkedHashMap<>();
        for (Transaction transaction : transactions.findByPortfolioIdAndPortfolioUserIdOrderByTransactionDateAscIdAsc(portfolioId, userId))
            histories.computeIfAbsent(transaction.getAsset().getId(), key -> new ArrayList<>()).add(transaction);
        List<Option> result = new ArrayList<>();
        for (List<Transaction> history : histories.values()) {
            BigDecimal balance = BigDecimal.ZERO;
            for (Transaction transaction : history) if (!transaction.getTransactionDate().isAfter(date)) balance = apply(balance, transaction);
            if (balance.signum() <= 0) continue;
            BigDecimal atDate = balance, limit = balance;
            for (Transaction transaction : history) if (transaction.getTransactionDate().isAfter(date)) {
                balance = apply(balance, transaction); limit = limit.min(balance);
            }
            var asset = history.getFirst().getAsset();
            result.add(new Option(asset.getId(), asset.getTicker(), asset.getName(), asset.getType(), atDate, limit.max(BigDecimal.ZERO)));
        }
        return result.stream().sorted(Comparator.comparing(Option::ticker).thenComparing(Option::assetId)).toList();
    }
    private BigDecimal apply(BigDecimal balance, Transaction transaction) {
        return transaction.getType() == TransactionType.BUY ? balance.add(transaction.getQuantity()) : balance.subtract(transaction.getQuantity());
    }
}
