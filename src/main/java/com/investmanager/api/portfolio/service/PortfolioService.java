package com.investmanager.api.portfolio.service;

import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.dto.CreatePortfolioRequest;
import com.investmanager.api.portfolio.dto.PortfolioResponse;
import com.investmanager.api.portfolio.mapper.PortfolioMapper;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final PortfolioMapper portfolioMapper;
    private final UserRepository userRepository;

    public PortfolioService(
            PortfolioRepository portfolioRepository,
            PortfolioMapper portfolioMapper,
            UserRepository userRepository) {

        this.portfolioRepository = portfolioRepository;
        this.portfolioMapper = portfolioMapper;
        this.userRepository = userRepository;
    }

    public PortfolioResponse create(
            CreatePortfolioRequest request,
            Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Usuário não encontrado"));

        Portfolio portfolio = portfolioMapper.toEntity(request);

        portfolio.setUser(user);

        Portfolio savedPortfolio =
                portfolioRepository.save(portfolio);

        return portfolioMapper.toResponse(savedPortfolio);
    }

    public PortfolioResponse findById(
            Long id,
            Long userId) {

        Portfolio portfolio =
                portfolioRepository
                        .findByIdAndUserId(id, userId)
                        .orElseThrow(() ->
                                new PortfolioNotFoundException(id));

        return portfolioMapper.toResponse(portfolio);
    }

    public List<PortfolioResponse> findAll(Long userId) {

        return portfolioRepository
                .findAllByUserId(userId)
                .stream()
                .map(portfolioMapper::toResponse)
                .toList();
    }
}
