package com.trading.marketdataservice.service;


import com.trading.marketdataservice.dto.StockPrice;
import com.trading.marketdataservice.entity.Stock;
import com.trading.marketdataservice.repository.StockRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketDataService {

    private final StockRepository stockRepository;

    /*
     * Concurrent HashMap - for Thread Safety
     */

    private final Map<String, StockPrice> stockPrices =
            new ConcurrentHashMap<>();


    /*
     *
     *   initialize Stock Prices
     *
     *  PostConstruct -> runs when context is ready
     */

    @PostConstruct
    public void initializePrices() {
        log.info("Loading stock data .....");

        List<Stock> stocks = stockRepository.findByActiveTrue();


        if (stocks.isEmpty()) {
            log.warn("No Active Stocks Found !");
            return;
        }


        stocks.forEach(stock -> {
            StockPrice stockPrice = new StockPrice();
            stockPrice.setSymbol(stock.getSymbol());
            stockPrice.setPrice(stock.getInitialPrice());
            stockPrice.setCompanyName(stock.getCompanyName());
            stockPrice.setOpen(stock.getInitialPrice());
            stockPrice.setHigh(stock.getInitialPrice());
            stockPrice.setLow(stock.getInitialPrice());
            stockPrice.setChange(BigDecimal.ZERO);
            stockPrice.setChangePercent(BigDecimal.ZERO);
            stockPrice.setVolume(0L);
            stockPrice.setTimestamp(LocalDateTime.now());


            stockPrices.put(stock.getSymbol(), stockPrice);

            log.info("Loaded Stock : {} at {}", stock.getSymbol(), stock.getInitialPrice());
        });

        log.info("Initial {} stocks", stockPrices.size());
    }


    /*
     *  Scheduled Price Update - every 2 seconds
     */

    @Scheduled(fixedDelayString = "${market.price-update-interval}")
    public void updatePrices() {

        if (stockPrices.isEmpty()) {
            log.warn("No Stocks Loaded");
            return;
        }

        stockPrices.forEach((symbol, currentPrice) ->{
             
        });

    }
}
