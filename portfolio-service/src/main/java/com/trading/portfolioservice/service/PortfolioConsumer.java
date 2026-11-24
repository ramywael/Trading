package com.trading.portfolioservice.service;


import com.trading.portfolioservice.entity.Holding;
import com.trading.portfolioservice.repository.HoldingRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
@AllArgsConstructor
@Slf4j
public class PortfolioConsumer {


    private HoldingRepository holdingRepository;


    @KafkaListener(topics = "order.executed")
    public void consumeOrderExecuted(@Payload Map<String, Object> payLoad) {

        try {
            String symbol = (String) payLoad.get("symbol");
            String userId = (String) payLoad.get("userId");
            String type = (String) payLoad.get("type");


            int quantity = Integer.parseInt((String) payLoad.get("quantity"));

            BigDecimal price = new BigDecimal(
                    (String) payLoad.get("price")
            );


            log.info("Updating Portfolio - user: {} {} x {} at {}", userId, symbol, quantity, price);

            if (type.equalsIgnoreCase("buy")) {
                addHolding(userId, price, symbol, quantity);
            } else if (type.equalsIgnoreCase("sell")) {
                removeHolding(userId, symbol, quantity);
            }

        } catch (Exception e) {
            log.error("Failed Updating Portfolio : {}",e.getMessage());
        }

    }

    /**
     * Add Or Update The Stocks While
     * Making A Buy Order Type
     *
     * @param userId
     * @param price
     * @param symbol
     * @param quantity
     */
    private void addHolding(String userId, BigDecimal price, String symbol, int quantity) {
        Holding holding = holdingRepository.findByUserIdAndSymbol(userId, symbol)
                .orElse(null);

        if (holding == null) {
            // First Time to Buy This Stock
            holding.setUserId(userId);
            holding.setCompanyName(symbol);
            holding.setQuantity(quantity);
            holding.setTotalInvested(BigDecimal.valueOf(quantity).multiply(price));
            holding.setAverageBuyPrice(price);
        } else {
            // Already HAVING THIS Stock -update average price

            int newQuantity = holding.getQuantity() + quantity;

            BigDecimal newTotalInvested = holding.getTotalInvested()
                    .multiply(BigDecimal.valueOf(quantity).multiply(price));

            BigDecimal newAvgPrice = newTotalInvested.divide(newTotalInvested, 4, RoundingMode.HALF_UP);

            holding.setAverageBuyPrice(newAvgPrice);
            holding.setQuantity(newQuantity);
            holding.setTotalInvested(newTotalInvested);

        }

        holdingRepository.save(holding);
        log.info("Stock Updated: {} {} shares of {}", userId, holding.getQuantity(), symbol);
    }


    private void removeHolding(String userId, String symbol, int quantity) {
        Holding holding = holdingRepository.findByUserIdAndSymbol(userId, symbol)
                .orElseThrow(() -> new RuntimeException(
                        "Holding Not Found" + symbol
                ));

        if (holding.getQuantity() < quantity) {
            throw new RuntimeException("Insufficient Shares To Sell");
        }


        int newQuantity = holding.getQuantity() - quantity;

        if (newQuantity == 0) {
            holdingRepository.delete(holding);
            log.info("Holding remove: {} sold all {}", userId, symbol);
        } else {
            BigDecimal newTotalInvested = holding.getAverageBuyPrice()
                    .multiply(BigDecimal.valueOf(newQuantity));

            holding.setQuantity(newQuantity);
            holding.setTotalInvested(newTotalInvested);
            holdingRepository.save(holding);

            log.info("Holding Reduces : {} now has {} shares of {}", userId, newQuantity, symbol);
        }

    }
}
