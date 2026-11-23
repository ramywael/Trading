package com.trading.portfolioservice.service;


import com.trading.portfolioservice.entity.Holding;
import com.trading.portfolioservice.repository.HoldingRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@AllArgsConstructor
public class PortfolioConsumer {


    private HoldingRepository holdingRepository;

    /**
     * Add Or Update The Stocks While
     * Making A Buy Order Type
     * @param userId
     * @param price
     * @param symbol
     * @param quantity
     */
    private void addHolding(String userId, BigDecimal price,String symbol,int quantity){
        Holding holding = HoldingRepository.findByUserIdAndSymbol(userId,symbol)
                .orElse(null);
        if(holding == null){
            holding.setUserId(userId);

        }


    }
}
