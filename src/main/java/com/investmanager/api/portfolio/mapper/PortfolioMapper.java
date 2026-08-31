package com.investmanager.api.portfolio.mapper;

import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.dto.CreatePortfolioRequest;
import com.investmanager.api.portfolio.dto.PortfolioResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import java.time.LocalDateTime;

@Mapper(componentModel = "spring")
public interface PortfolioMapper {

    Portfolio toEntity(CreatePortfolioRequest request);

    PortfolioResponse toResponse(Portfolio portfolio);

    default void setCreatedAt(@MappingTarget Portfolio portfolio) {
        portfolio.initializeCreatedAt();
    }
}
