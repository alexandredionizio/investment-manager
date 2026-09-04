package com.investmanager.api.broker.controller;

import com.investmanager.api.broker.dto.BrokerRequest;
import com.investmanager.api.broker.dto.BrokerResponse;
import com.investmanager.api.broker.mapper.BrokerMapper;
import com.investmanager.api.broker.service.BrokerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/brokers")
public class BrokerController {

    private final BrokerMapper brokerMapper;
    public BrokerService brokerService;

    public BrokerController(BrokerService brokerService, BrokerMapper brokerMapper) {
        this.brokerService = brokerService;
        this.brokerMapper = brokerMapper;
    }

    @PostMapping
    public ResponseEntity<BrokerResponse> create(
            @Valid @RequestBody BrokerRequest request) {

        BrokerResponse response = brokerService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<BrokerResponse>> findAll() {
        return ResponseEntity.ok(
                brokerService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BrokerResponse> findById(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                brokerService.findById(id));
    }
}
