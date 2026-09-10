package com.investmanager.api.user.service;

import com.investmanager.api.user.dto.UserCreateRequest;
import com.investmanager.api.user.dto.UserResponse;
import com.investmanager.api.user.entity.User;
import com.investmanager.api.user.exception.UserAlreadyExistsException;
import com.investmanager.api.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

@Service
public class UserService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserResponse create(UserCreateRequest request) {

        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new UserAlreadyExistsException();
        }

        String encodedPassword = passwordEncoder.encode(request.password());

        User user = new User(
                request.name(),
                request.email(),
                encodedPassword
        );

        User savedUser = userRepository.save(user);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getCreatedAt()
        );
    }
}
