package com.investmanager.api.broker.controller;

import com.investmanager.api.broker.dto.BrokerRequest;
import com.investmanager.api.broker.dto.BrokerResponse;
import com.investmanager.api.broker.service.BrokerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/brokers")
@Tag(
        name = "Brokers",
        description = "Endpoints for managing investment brokers"
)
public class BrokerController {

    private final BrokerService brokerService;

    public BrokerController(
            BrokerService brokerService) {
        this.brokerService = brokerService;
    }

    @Operation(
            summary = "Create broker",
            description = "Creates a new investment broker"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Broker created successfully"
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            )
    })
    @PostMapping
    public ResponseEntity<BrokerResponse> create(
            @Valid @RequestBody BrokerRequest request) {

        BrokerResponse response =
                brokerService.create(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @Operation(
            summary = "List brokers",
            description = "Returns all investment brokers"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Brokers retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            )
    })
    @GetMapping
    public ResponseEntity<List<BrokerResponse>> findAll() {

        return ResponseEntity.ok(
                brokerService.findAll()
        );
    }

    @Operation(
            summary = "Find broker by ID",
            description = "Returns an investment broker by its ID"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Broker found successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Broker not found"
            )
    })
    @GetMapping("/{id}")
    public ResponseEntity<BrokerResponse> findById(
            @PathVariable Long id) {

        return ResponseEntity.ok(
                brokerService.findById(id)
        );
    }
}