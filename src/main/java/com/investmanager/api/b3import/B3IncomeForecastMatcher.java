package com.investmanager.api.b3import;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/** Read-only classification. Recomputed under a transaction lock before any settlement. */
@Service
public class B3IncomeForecastMatcher {
    private final JdbcTemplate jdbc;

    public B3IncomeForecastMatcher(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Match(String status, Long expectedId, BigDecimal expectedAmount) {
        public static Match none() { return new Match("NONE", null, null); }
    }
    private record Candidate(Long id, BigDecimal amount, String institution) {}

    public Match classify(Long portfolioId, Long assetId, String type, LocalDate date,
                          BigDecimal quantity, BigDecimal credited, String institution) {
        if (portfolioId == null || assetId == null || type == null || date == null ||
                quantity == null || credited == null) return Match.none();
        List<Candidate> candidates = jdbc.query("""
                select id, expected_net_amount, institution from b3_expected_incomes
                 where portfolio_id = ? and asset_id = ? and income_type = ?
                   and expected_payment_date = ? and quantity = ? and matched_income_id is null
                """, (rs, n) -> new Candidate(rs.getLong(1), rs.getBigDecimal(2), rs.getString(3)),
                portfolioId, assetId, type, date, quantity);
        if (candidates.isEmpty()) return Match.none();
        // The B3 institution must agree. A missing institution cannot establish identity.
        String source = normalize(institution);
        if (source.isEmpty()) return new Match("AMBIGUOUS", null, null);
        List<Candidate> matchingInstitution = candidates.stream()
                .filter(candidate -> source.equals(normalize(candidate.institution()))).toList();
        if (matchingInstitution.size() != 1) return new Match("AMBIGUOUS", null, null);
        Candidate candidate = matchingInstitution.getFirst();
        if (candidate.amount().subtract(credited).abs().compareTo(new BigDecimal("0.01")) <= 0)
            return new Match("EXACT", candidate.id(), candidate.amount());
        return new Match("DIFFERENT_AMOUNT", candidate.id(), candidate.amount());
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
