package com.investmanager.api.b3import;

import com.investmanager.api.portfolio.repository.PortfolioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Consultas de previsões importadas; não cria créditos nem assume conciliação automática. */
@RestController
@RequestMapping("/api/v1/expected-incomes")
public class B3ExpectedIncomeQueryController {
    private final JdbcTemplate jdbc;
    private final PortfolioRepository portfolios;

    public B3ExpectedIncomeQueryController(JdbcTemplate jdbc, PortfolioRepository portfolios) {
        this.jdbc = jdbc;
        this.portfolios = portfolios;
    }

    public record ExpectedIncomeView(Long id, Long portfolioId, Long assetId, String assetTicker,
            String type, LocalDate expectedPaymentDate, BigDecimal quantity,
            BigDecimal expectedNetAmount, String institution) {}

    @GetMapping("/portfolio/{portfolioId}")
    public List<ExpectedIncomeView> list(@PathVariable Long portfolioId, Authentication authentication) {
        if (authentication == null || portfolioId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        Long userId;
        try { userId = Long.valueOf(authentication.getName()); }
        catch (NumberFormatException exception) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
        if (portfolios.findByIdAndUserId(portfolioId, userId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Carteira não encontrada.");
        }
        return jdbc.query("""
            select e.id, e.portfolio_id, e.asset_id, a.ticker, e.income_type,
                   e.expected_payment_date, e.quantity, e.expected_net_amount, e.institution
            from b3_expected_incomes e
            join assets a on a.id = e.asset_id
            where e.portfolio_id = ? and e.matched_income_id is null
            order by e.expected_payment_date, e.id
            """, (rs, row) -> new ExpectedIncomeView(
                rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                rs.getString(5), rs.getObject(6, LocalDate.class),
                rs.getBigDecimal(7), rs.getBigDecimal(8), rs.getString(9)), portfolioId);
    }
}
