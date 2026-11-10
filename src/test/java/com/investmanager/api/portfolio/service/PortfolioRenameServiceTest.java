package com.investmanager.api.portfolio.service;

import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.dto.RenamePortfolioRequest;
import com.investmanager.api.portfolio.dto.PortfolioResponse;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.portfolio.mapper.PortfolioMapper;
import com.investmanager.api.portfolio.repository.PortfolioRepository;
import com.investmanager.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PortfolioRenameServiceTest {
    @Test void renamesOnlyOwnedPortfolio() {
        var repo = mock(PortfolioRepository.class);
        var mapper = mock(PortfolioMapper.class);
        var service = new PortfolioService(repo, mapper, mock(UserRepository.class));
        var portfolio = new Portfolio();
        portfolio.setName("Antiga");
        when(repo.findByIdAndUserId(9L, 4L)).thenReturn(Optional.of(portfolio));
        when(mapper.toResponse(portfolio)).thenAnswer(inv -> new PortfolioResponse(9L, portfolio.getName(), null, LocalDateTime.now()));
        var result = service.rename(9L, new RenamePortfolioRequest(" Nova "), 4L);
        assertEquals("Nova", result.name());
        verify(repo).findByIdAndUserId(9L, 4L);
        verify(repo, never()).delete(any());
    }

    @Test void deniesNonOwnedPortfolio() {
        var repo = mock(PortfolioRepository.class);
        var service = new PortfolioService(repo, mock(PortfolioMapper.class), mock(UserRepository.class));
        when(repo.findByIdAndUserId(9L, 5L)).thenReturn(Optional.empty());
        assertThrows(PortfolioNotFoundException.class,
                () -> service.rename(9L, new RenamePortfolioRequest("Novo nome"), 5L));
    }
}
