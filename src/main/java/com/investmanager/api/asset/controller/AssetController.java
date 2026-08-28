package com.investmanager.api.asset.controller;

import com.investmanager.api.asset.dto.AssetResponse;
import com.investmanager.api.asset.dto.CreateAssetRequest;
import com.investmanager.api.asset.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AssetResponse create(@Valid @RequestBody CreateAssetRequest request) {
        return assetService.create(request);
    }

    @GetMapping("/{id}")
    public AssetResponse findById(@PathVariable Long id) {
        return assetService.findById(id);
    }

    @GetMapping
    public List<AssetResponse> findAll(){
        return assetService.findAll();
    }
}
