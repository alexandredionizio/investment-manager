package com.investmanager.api.shared.exception;

import com.investmanager.api.asset.exception.AssetNotFoundException;
import com.investmanager.api.broker.exception.BrokerNotFoundException;
import com.investmanager.api.income.exception.IncomeNotFoundException;
import com.investmanager.api.portfolio.exception.PortfolioNotFoundException;
import com.investmanager.api.transaction.exception.TransactionNotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** IM-058: identificadores técnicos não aparecem nas respostas REST 404. */
class NotFoundErrorPresentationTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void numericDatabaseIdentifiersAreNotExposedInNotFoundResponses() {
        ErrorResponse[] responses = {
                handler.handleAssetNotFoundException(new AssetNotFoundException(912347L)),
                handler.handlePortfolioNotFoundException(new PortfolioNotFoundException(912347L)),
                handler.handleTransactionNotFoundException(new TransactionNotFoundException(912347L)),
                handler.handleBrokerNotFoundException(new BrokerNotFoundException(912347L)),
                handler.handleIncomeNotFoundException(new IncomeNotFoundException(912347L))
        };
        for (ErrorResponse response : responses) {
            assertEquals(404, response.status());
            assertFalse(response.message().contains("912347"));
            assertFalse(response.message().toLowerCase().contains("id:"));
            assertTrue(response.message().endsWith("não encontrado.") || response.message().endsWith("não encontrada."));
        }
    }
}
