package com.investmanager.api.quote.service;

import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.BrapiQuoteData;
import com.investmanager.api.quote.client.dto.BrapiQuoteResponse;
import com.investmanager.api.quote.client.dto.BrapiQuoteResult;
import com.investmanager.api.quote.exception.QuoteNotFoundException;
import com.investmanager.api.quote.service.QuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class QuoteServiceTest {

    @Mock
    private BrapiClient brapiClient;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private QuoteService quoteService;

    @BeforeEach
    void setUp() {

        when(redisTemplate.opsForValue())
                .thenReturn(valueOperations);

        quoteService = new QuoteService(
                brapiClient,
                redisTemplate
        );
    }

    @Test
    void shouldReturnCurrentPrice() {

        BrapiQuoteData data = new BrapiQuoteData(
                "PETR4",
                "BRL",
                new BigDecimal("47.00"),
                new BigDecimal("1.25")
        );

        BrapiQuoteResult result = new BrapiQuoteResult(
                "PETR4",
                "PETR4",
                data
        );

        BrapiQuoteResponse response =
                new BrapiQuoteResponse(List.of(result));

        when(valueOperations.get("quote:PETR4"))
                .thenReturn(null);

        when(brapiClient.getQuote("PETR4"))
                .thenReturn(response);

        BigDecimal price =
                quoteService.getCurrentPrice("PETR4");

        assertEquals(new BigDecimal("47.00"), price);

        verify(brapiClient).getQuote("PETR4");

        verify(valueOperations).set(
                "quote:PETR4",
                "47.00",
                Duration.ofMinutes(5)
        );
    }

    @Test
    void shouldThrowExceptionWhenQuoteNotFound() {

        when(valueOperations.get("quote:ABCXYZ"))
                .thenReturn(null);

        when(brapiClient.getQuote("ABCXYZ"))
                .thenThrow(new QuoteNotFoundException("ABCXYZ"));

        assertThrows(
                QuoteNotFoundException.class,
                () -> quoteService.getCurrentPrice("ABCXYZ")
        );

        verify(brapiClient).getQuote("ABCXYZ");
    }

    @Test
    void shouldThrowExceptionWhenQuoteResponseIsEmpty() {

        BrapiQuoteResponse response =
                new BrapiQuoteResponse(List.of());

        when(valueOperations.get("quote:ABCXYZ"))
                .thenReturn(null);

        when(brapiClient.getQuote("ABCXYZ"))
                .thenReturn(response);

        assertThrows(
                QuoteNotFoundException.class,
                () -> quoteService.getCurrentPrice("ABCXYZ")
        );

        verify(brapiClient).getQuote("ABCXYZ");
    }

    @Test
    void shouldReturnPriceFromCacheWithoutCallingBrapi() {

        when(valueOperations.get("quote:PETR4"))
                .thenReturn("47.00");

        BigDecimal price =
                quoteService.getCurrentPrice("PETR4");

        assertEquals(new BigDecimal("47.00"), price);

        verifyNoInteractions(brapiClient);
    }
}
