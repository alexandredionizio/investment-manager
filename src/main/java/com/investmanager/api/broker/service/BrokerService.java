package com.investmanager.api.broker.service;

import com.investmanager.api.broker.Broker;
import com.investmanager.api.broker.dto.BrokerRequest;
import com.investmanager.api.broker.dto.BrokerResponse;
import com.investmanager.api.broker.exception.BrokerNotFoundException;
import com.investmanager.api.broker.mapper.BrokerMapper;
import com.investmanager.api.broker.repository.BrokerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BrokerService {

    private final BrokerRepository brokerRepository;
    private final BrokerMapper brokerMapper;

    public BrokerService(
            BrokerRepository brokerRepository,
            BrokerMapper brokerMapper) {

        this.brokerRepository = brokerRepository;
        this.brokerMapper = brokerMapper;
    }

    public BrokerResponse create(BrokerRequest request) {

        Broker broker = brokerMapper.toEntity(request);

        Broker savedBroker = brokerRepository.save(broker);

        return  brokerMapper.toResponse(savedBroker);
    }

    public List<BrokerResponse> findAll() {
        return brokerRepository.findAll()
                .stream()
                .map(brokerMapper::toResponse)
                .toList();
    }

    public BrokerResponse findById(Long id) {
        Broker broker = brokerRepository.findById(id)
                .orElseThrow(()-> new BrokerNotFoundException(id));

        return brokerMapper.toResponse(broker);
    }
}
