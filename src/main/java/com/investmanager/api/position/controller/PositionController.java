package com.investmanager.api.position.controller;

import com.investmanager.api.position.dto.PositionResponse;
import com.investmanager.api.position.service.PositionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/portfolios/{portfolioId}/positions")
public class PositionController {

    private final PositionService positionService;

    public PositionController(PositionService positionService) {
        this.positionService = positionService;
    }

    @GetMapping
    public ResponseEntity<List<PositionResponse>> findPositions (
            @PathVariable Long portfolioId) {

        return ResponseEntity.ok(
                positionService.calculatePositions(portfolioId)
        );
    }
}
