package com.investmanager.api.portfoliohistory.capital;

import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Captures ONLY true external cash transfers. Does not claim to compute an account's cash balance. */
@Service
public class CapitalMovementService {
    private final PortfolioRepository portfolios;
    private final CapitalMovementRepository movements;

    public CapitalMovementService(PortfolioRepository portfolios, CapitalMovementRepository movements) {
        this.portfolios = portfolios;
        this.movements = movements;
    }

    @Transactional(readOnly = true)
    public List<CapitalMovementResponse> list(Long portfolioId, Long userId) {
        ownedPortfolio(portfolioId, userId);
        return movements.findByPortfolio_IdOrderByMovementDateAscIdAsc(portfolioId)
                .stream().map(CapitalMovementResponse::from).toList();
    }

    @Transactional
    public CapitalMovementResponse create(Long portfolioId, Long userId, CapitalMovementRequest request) {
        Portfolio portfolio = ownedPortfolio(portfolioId, userId);
        if (request == null || request.requestId() == null || request.kind() == null || request.date() == null ||
                request.amount() == null || request.amount().signum() <= 0 || request.amount().scale() > 2 ||
                request.amount().precision() - request.amount().scale() > 17 ||
                (request.description() != null && request.description().length() > 255)) {
            throw new IllegalArgumentException("Movimentação externa inválida");
        }
        // Never silently treat an old requestId with a different payload as a new transfer.
        var existing = movements.findByPortfolio_IdAndRequestId(portfolioId, request.requestId());
        if (existing.isPresent()) {
            CapitalMovement prior = existing.get();
            if (samePayload(prior, request)) return CapitalMovementResponse.from(prior);
            throw new IllegalArgumentException("Identificador de solicitação já utilizado com outros dados");
        }
        CapitalMovement saved = movements.saveAndFlush(new CapitalMovement(portfolio, request.requestId(),
                request.kind(), request.date(), request.amount().setScale(2), request.description()));
        return CapitalMovementResponse.from(saved);
    }

    static boolean samePayload(CapitalMovement item, CapitalMovementRequest request) {
        return item.getKind() == request.kind()
                && item.getMovementDate().equals(request.date())
                && item.getAmount().compareTo(request.amount()) == 0
                && java.util.Objects.equals(item.getDescription(), request.description());
    }

    private Portfolio ownedPortfolio(Long portfolioId, Long userId) {
        return portfolios.findByIdAndUserId(portfolioId, userId)
                .orElseThrow(() -> new PortfolioNotFoundException(portfolioId));
    }
}
