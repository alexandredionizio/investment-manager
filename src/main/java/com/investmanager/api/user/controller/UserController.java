package com.investmanager.api.user.controller;

import com.investmanager.api.user.dto.UserCreateRequest;
import com.investmanager.api.user.dto.UserResponse;
import com.investmanager.api.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create (
            @RequestBody @Valid UserCreateRequest request) {

        UserResponse response = userService.create(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
