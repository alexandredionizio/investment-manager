package com.investmanager.api.broker.mapper;

import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.dto.BrokerRequest;
import com.investmanager.api.broker.dto.BrokerResponse;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BrokerMapper {

    Broker toEntity(BrokerRequest request);

    BrokerResponse toResponse(Broker broker);
}
