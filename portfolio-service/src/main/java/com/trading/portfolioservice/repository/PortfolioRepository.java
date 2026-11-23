package com.trading.portfolioservice.repository;

import com.trading.portfolioservice.entity.Holding;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PortfolioRepository extends JpaRepository<Holding,String> {
    public List<Holding> findByUserId(String userId);
}
