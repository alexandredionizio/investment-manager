package com.investmanager.api.user.service;

import com.investmanager.api.user.dto.UserCreateRequest;
import com.investmanager.api.user.dto.UserResponse;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.exception.UserAlreadyExistsException;
import com.investmanager.api.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void shouldCreateUserWhenEmailDoesNotExist() {

        UserCreateRequest request = new UserCreateRequest(
                "Alexandre",
                "alexandre@email.com",
                "MinhaSenha123"
        );

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.empty());

        when(passwordEncoder.encode(request.password()))
                .thenReturn("senhaCodificada");

        User user  = new User(
                "Alexandre",
                "alexandre@email.com",
                "senhaCodificada"
        );

        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        UserResponse response = userService.create(request);

        assertEquals("Alexandre",response.name());
        assertEquals("alexandre@email.com", response.email());

        verify(passwordEncoder).encode(request.password());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User capturedUser = userCaptor.getValue();

        assertEquals("senhaCodificada",capturedUser.getPassword());
    }

    @Test
    void shouldThrowExceptionWhenEmailAlreadyExists() {

        UserCreateRequest request = new UserCreateRequest(
                "Alexandre",
                "alexandre@email.com",
                "MinhaSenha123"
        );

        User existingUser = new User(
                "Alexandre",
                "alexandre@email.com",
                "senhaCodificada"
        );

        when(userRepository.findByEmail(request.email()))
                .thenReturn(Optional.of(existingUser));

        UserAlreadyExistsException exception = assertThrows(
                UserAlreadyExistsException.class,
                () -> userService.create(request)
        );

        assertEquals("E-mail já cadastrado", exception.getMessage());

        verifyNoInteractions(passwordEncoder);
        verify(userRepository, never()).save(any(User.class));
    }
}
