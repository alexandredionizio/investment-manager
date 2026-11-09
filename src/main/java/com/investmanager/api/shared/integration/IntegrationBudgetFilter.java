package com.investmanager.api.shared.integration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Component
public class IntegrationBudgetFilter extends OncePerRequestFilter {
    private static final ThreadLocal<Long> DEADLINE = new ThreadLocal<>();
    private final IntegrationProperties properties;
    public IntegrationBudgetFilter(IntegrationProperties properties) { this.properties = properties; }
    public static long remainingNanos(long operationNanos) {
        Long deadline = DEADLINE.get();
        return deadline == null ? operationNanos : Math.min(operationNanos, deadline - System.nanoTime());
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        DEADLINE.set(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(properties.getRequestBudgetMs()));
        try { chain.doFilter(request, response); }
        finally { DEADLINE.remove(); }
    }
}
