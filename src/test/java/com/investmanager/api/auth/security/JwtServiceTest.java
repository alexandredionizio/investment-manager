package com.investmanager.api.auth.security;

import com.investmanager.api.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtServiceTest {

    @Test
    void shouldGenerateToken() {

        // Arrange
        String secret =
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

        long expiration = 3600000;

        JwtService jwtService =
                new JwtService(secret, expiration);

        User user = mock(User.class);

        when(user.getId()).thenReturn(2L);

        // Act
        String token = jwtService.generateToken(user);

        // Assert
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);
    }

    @Test
    void shouldExtractSubjectFromToken() {

        // Arrange
        String secret =
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

        long expiration = 3600000;

        JwtService jwtService =
                new JwtService(secret, expiration);

        User user = mock(User.class);

        when(user.getId()).thenReturn(2L);

        String token = jwtService.generateToken(user);

        // Act
        String subject = jwtService.extractSubject(token);

        // Assert
        assertEquals("2", subject);
    }

    @Test
    void shouldReturnTrueWhenTokenIsValid() {

        // Arrange
        String secret =
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

        long expiration = 3600000;

        JwtService jwtService =
                new JwtService(secret, expiration);

        User user = mock(User.class);
        when(user.getId()).thenReturn(2L);

        String token = jwtService.generateToken(user);

        // Act
        boolean valid = jwtService.isTokenValid(token);

        // Assert
        assertTrue(valid);
    }

    @Test
    void shouldReturnFalseWhenTokenIsInvalid() {

        // Arrange
        String secret =
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

        long expiration = 3600000;

        JwtService jwtService =
                new JwtService(secret, expiration);

        String invalidToken = "token-invalido";

        // Act
        boolean valid = jwtService.isTokenValid(invalidToken);

        // Assert
        assertFalse(valid);
    }
}