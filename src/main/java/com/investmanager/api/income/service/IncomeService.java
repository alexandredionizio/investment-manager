package com.investmanager.api.income.service;

import com.investmanager.api.asset.Asset;
import com.investmanager.api.asset.exception.AssetNotFoundException;
import com.investmanager.api.asset.repository.AssetRepository;
import com.investmanager.api.income.Income;
import com.investmanager.api.income.dto.IncomeRequest;
import com.investmanager.api.income.dto.IncomeResponse;
import com.investmanager.api.income.exception.IncomeNotFoundException;
import com.investmanager.api.income.mapper.IncomeMapper;
import com.investmanager.api.income.repository.IncomeRepository;
import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class IncomeService {

    private final IncomeRepository incomeRepository;
    private final PortfolioRepository portfolioRepository;
    private final AssetRepository assetRepository;
    private final IncomeMapper incomeMapper;

    public IncomeService(
            IncomeRepository incomeRepository,
            PortfolioRepository portfolioRepository,
            AssetRepository assetRepository,
            IncomeMapper incomeMapper) {

        this.incomeRepository = incomeRepository;
        this.portfolioRepository = portfolioRepository;
        this.assetRepository = assetRepository;
        this.incomeMapper = incomeMapper;
    }

    public IncomeResponse create (IncomeRequest request) {

        Portfolio portfolio = portfolioRepository
                .findById(request.portfolioId())
                .orElseThrow(() ->
                        new PortfolioNotFoundException(request.portfolioId()));

        Asset asset = assetRepository
                .findById(request.assetId())
                .orElseThrow(() ->
                        new AssetNotFoundException(request.assetId()));

        Income income = new Income(
                portfolio,
                asset,
                request.type(),
                request.amountPerUnit(),
                request.quantity(),
                request.paymentDate()
        );

        Income savedIncome = incomeRepository.save(income);

        return incomeMapper.toResponse(savedIncome);
    }

    @Transactional(readOnly = true)
    public IncomeResponse findById(Long id) {

        Income income = incomeRepository
                .findById(id)
                .orElseThrow(() -> new IncomeNotFoundException(id));

        return incomeMapper.toResponse(income);
    }

    @Transactional(readOnly = true)
    public List<IncomeResponse> findAll() {
        return incomeRepository
                .findAll()
                .stream()
                .map(incomeMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<IncomeResponse> findByPortfolioId(Long portfolioid) {
        return incomeRepository
                .findByPortfolioIdOrderByPaymentDateAscIdAsc(portfolioid)
                .stream()
                .map(incomeMapper::toResponse)
                .toList();
    }
}
