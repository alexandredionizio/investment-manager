package com.investmanager.api.auth.service;

import com.investmanager.api.auth.dto.LoginRequest;
import com.investmanager.api.auth.dto.LoginResponse;
import com.investmanager.api.auth.exception.InvalidCredentialsException;
import com.investmanager.api.auth.security.JwtService;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void shouldLoginWhenCredentialsAreValid() {

        LoginRequest request = new LoginRequest(
                "alexandre@email.com",
                "MinhaSenha123"
        );

        User user = new User(
                "Alexandre",
                "alexandre@email.com",
                "senhaCodificada"
        );

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                request.password(),
                user.getPassword()))
                .thenReturn(true);

        when(jwtService.generateToken(user))
                .thenReturn("token-jwt");

        LoginResponse response = authService.login(request);

        assertEquals("token-jwt", response.token());

        verify(jwtService).generateToken(user);
    }

    @Test
    void shouldThrowExceptionWhenEmailDoesNotExist() {

        LoginRequest request = new LoginRequest(
                "inexistente@email.com",
                "MinhaSenha123"
        );

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.empty());

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        assertEquals(
                "E-mail ou senha inválidos",
                exception.getMessage()
        );

        verifyNoInteractions(passwordEncoder);
        verifyNoInteractions(jwtService);
    }

    @Test
    void shouldThrowExceptionWhenPasswordIsInvalid() {

        LoginRequest request = new LoginRequest(
                "alexandre@email.com",
                "SenhaErrada"
        );

        User user = new User(
                "Alexandre",
                "alexandre@email.com",
                "senhaCodificada"
        );

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                request.password(),
                user.getPassword()))
                .thenReturn(false);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );

        assertEquals(
                "E-mail ou senha inválidos",
                exception.getMessage()
        );

        verify(jwtService, never())
                .generateToken(any(User.class));
    }
}