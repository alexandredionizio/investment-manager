package com.investmanager.api.income.repository;

import com.investmanager.api.income.Income;
import com.investmanager.api.portfolio.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.w3c.dom.stylesheets.LinkStyle;

import java.util.List;

public interface IncomeRepository extends JpaRepository<Income, Long> {

    List<Income> findByPortfolioIdOrderByPaymentDateAscIdAsc(Long portfolio);
}
