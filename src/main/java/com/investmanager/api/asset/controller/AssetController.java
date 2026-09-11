package com.investmanager.api.asset.controller;

import com.investmanager.api.asset.dto.AssetResponse;
import com.investmanager.api.asset.dto.CreateAssetRequest;
import com.investmanager.api.asset.service.AssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
@Tag(
        name = "Assets",
        description = "Endpoints for managing investment assets"
)
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @Operation(
            summary = "Create asset",
            description = "Creates a new investment asset"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Asset created successfully"
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
    @ResponseStatus(HttpStatus.CREATED)
    public AssetResponse create(
            @Valid @RequestBody CreateAssetRequest request) {

        return assetService.create(request);
    }

    @Operation(
            summary = "Find asset by ID",
            description = "Returns an investment asset by its ID"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Asset found successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Asset not found"
            )
    })
    @GetMapping("/{id}")
    public AssetResponse findById(@PathVariable Long id) {
        return assetService.findById(id);
    }

    @Operation(
            summary = "List assets",
            description = "Returns all investment assets"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Assets retrieved successfully"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "User not authenticated"
            )
    })
    @GetMapping
    public List<AssetResponse> findAll() {
        return assetService.findAll();
    }
}