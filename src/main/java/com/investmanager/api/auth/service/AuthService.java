package com.investmanager.api.auth.service;

import com.investmanager.api.auth.dto.LoginRequest;
import com.investmanager.api.auth.dto.LoginResponse;
import com.investmanager.api.auth.exception.InvalidCredentialsException;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.repository.UserRepository;
import com.investmanager.api.auth.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {

        User user = userRepository
                .findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(
                request.password(),
                user.getPassword())) {

            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(token);
    }
}
