package com.investmanager.api.quote.service;

import com.investmanager.api.quote.client.BrapiClient;
import com.investmanager.api.quote.client.dto.BrapiQuoteResponse;
import com.investmanager.api.quote.client.dto.BrapiQuoteResult;
import com.investmanager.api.quote.exception.QuoteNotFoundException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;

@Service
public class QuoteService {

    private final BrapiClient brapiClient;
    private final StringRedisTemplate redisTemplate;

    public QuoteService(
            BrapiClient brapiClient,
            StringRedisTemplate stringRedisTemplate) {

        this.brapiClient = brapiClient;
        this.redisTemplate = stringRedisTemplate;
    }

    public BigDecimal getCurrentPrice (String symbol) {

        String cacheKey = "quote:" + symbol.toUpperCase();

        String cachedPrice =
                redisTemplate.opsForValue().get(cacheKey);

        if (cachedPrice != null) {
            return new BigDecimal(cachedPrice);
        }

        BrapiQuoteResponse response =
                brapiClient.getQuote(symbol);

        if (response == null ||
                response.results() == null ||
                response.results().isEmpty()) {

            throw new QuoteNotFoundException(symbol);
        }

        BrapiQuoteResult result =
                response.results().getFirst();

        BigDecimal price = result.data().regularMarketPrice();

        redisTemplate.opsForValue().set(
                cacheKey,
                price.toPlainString(),
                Duration.ofMinutes(5)
        );

        return price;
    }
}
