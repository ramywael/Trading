package com.trading.portfolioservice.service;

import com.trading.portfolioservice.entity.Holding;
import com.trading.portfolioservice.repository.PortfolioRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@AllArgsConstructor
public class PortfolioService {


    private PortfolioRepository portfolioRepository;


    public Map<String,Object> getPortfolio(String userId){

        List<Holding> userHoldings= portfolioRepository.findByUserId(userId);

        List<Map<String,Object>> portfolioItems = new ArrayList<>();


        BigDecimal totalInvested = BigDecimal.ZERO;
        BigDecimal currentValue = BigDecimal.ZERO;

        for(Holding holding : userHoldings){


        }


    }


}
