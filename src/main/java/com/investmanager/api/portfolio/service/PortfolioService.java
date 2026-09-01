package com.investmanager.api.portfolio.service;

import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.dto.CreatePortfolioRequest;
import com.investmanager.api.portfolio.dto.PortfolioResponse;
import com.investmanager.api.portfolio.mapper.PortfolioMapper;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;

    private final PortfolioMapper portfolioMapper;

    public PortfolioService(
            PortfolioRepository portfolioRepository,
            PortfolioMapper portfolioMapper) {

        this.portfolioRepository = portfolioRepository;
        this.portfolioMapper = portfolioMapper;
    }

    public PortfolioResponse create (CreatePortfolioRequest request) {

        Portfolio portfolio = portfolioMapper.toEntity(request);

        Portfolio savedPortfolio = portfolioRepository.save(portfolio);

        return portfolioMapper.toResponse(savedPortfolio);
    }

    public PortfolioResponse findById(Long id) {

        Portfolio portfolio = portfolioRepository.findById(id)
                .orElseThrow(() -> new PortfolioNotFoundException(id));

        return portfolioMapper.toResponse(portfolio);
    }

    public List<PortfolioResponse> findAll() {

        return portfolioRepository.findAll()
                .stream()
                .map(portfolioMapper::toResponse)
                .toList();
    }
}
