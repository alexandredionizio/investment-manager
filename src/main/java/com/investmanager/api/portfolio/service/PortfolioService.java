package com.investmanager.api.portfolio.service;

import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.dto.CreatePortfolioRequest;
import com.investmanager.api.portfolio.dto.PortfolioResponse;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.shared.exception.PortfolioNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;

    public PortfolioService(PortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    public PortfolioResponse create (CreatePortfolioRequest request) {
        Portfolio portfolio = new Portfolio(
                request.name(),
                request.description()
        );

        Portfolio savedPortfolio = portfolioRepository.save(portfolio);

        return new PortfolioResponse(
                savedPortfolio.getId(),
                savedPortfolio.getName(),
                savedPortfolio.getDescription(),
                savedPortfolio.getCreatedAt()
        );
    }

    public PortfolioResponse findById(Long id) {

        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new PortfolioNotFoundException(id));

        return new PortfolioResponse(
                portfolio.getId(),
                portfolio.getName(),
                portfolio.getDescription(),
                portfolio.getCreatedAt()
        );
    }

    public List<PortfolioResponse> findAll() {

        return portfolioRepository.findAll()
                .stream()
                .map(portfolio -> new PortfolioResponse(
                        portfolio.getId(),
                        portfolio.getName(),
                        portfolio.getDescription(),
                        portfolio.getCreatedAt()
                ))
                .toList();
    }
}
