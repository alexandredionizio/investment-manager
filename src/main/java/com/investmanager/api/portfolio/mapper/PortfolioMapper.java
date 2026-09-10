package com.investmanager.api.portfolio.mapper;

import com.investmanager.api.portfolio.Portfolio;
import com.investmanager.api.portfolio.dto.CreatePortfolioRequest;
import com.investmanager.api.portfolio.dto.PortfolioResponse;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public interface PortfolioMapper {

    @Mapping(target = "user", ignore = true)
    Portfolio toEntity(CreatePortfolioRequest request);

    PortfolioResponse toResponse(Portfolio portfolio);

    @AfterMapping
    default void setCreatedAt(@MappingTarget Portfolio portfolio) {
        portfolio.initializeCreatedAt();
    }
}