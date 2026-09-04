package com.investmanager.api.broker.repository;

import com.investmanager.api.broker.Broker;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrokerRepository extends JpaRepository<Broker, Long> {
}
