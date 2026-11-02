package com.trading.marketdataservice.service;


import com.trading.marketdataservice.dto.StockPrice;
import com.trading.marketdataservice.entity.Stock;
import com.trading.marketdataservice.repository.StockRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketDataService {

    private final StockRepository stockRepository;

    private final Random random = new Random();

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PRICE_KEY_PREFIX = "stock:price:";
    private static final String PRICE_UPDATED_TOPIC = "stock.price.updated";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private final SimpMessagingTemplate simpMessagingTemplate;

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

        stockPrices.forEach((symbol, currentPrice) -> {
            BigDecimal oldPrice = currentPrice.getPrice();

            // +- price movement

            double priceMovement = (random.nextDouble() - 0.5) * 0.5;

            BigDecimal priceChange = oldPrice.multiply(
                            BigDecimal.valueOf(
                                    priceMovement / 100))
                    .setScale(2, RoundingMode.HALF_UP);

            BigDecimal newPrice = oldPrice
                    .add(priceChange)
                    .setScale(2, RoundingMode.HALF_UP);


            //Prevent Negative Price
            if (newPrice.compareTo(BigDecimal.valueOf(1)) < 0) {
                newPrice = BigDecimal.valueOf(1);
            }

            //changeAmount

            BigDecimal changeAmount = newPrice
                    .subtract(currentPrice.getOpen())
                    .setScale(2, RoundingMode.HALF_UP);


            //Change Percent
            BigDecimal changePercent = changeAmount
                    .divide(currentPrice.getOpen(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);


            // UPDATE HIGH LOW

            BigDecimal newHigh = newPrice
                    .compareTo(currentPrice.getHigh()) > 0
                    ? newPrice : currentPrice.getHigh();


            BigDecimal newLow = newPrice
                    .compareTo(currentPrice.getLow()) < 0
                    ? newPrice : currentPrice.getLow();


            currentPrice.setPrice(newPrice);
            currentPrice.setChange(changeAmount);
            currentPrice.setChangePercent(changePercent);
            currentPrice.setHigh(newHigh);
            currentPrice.setLow(newLow);
            currentPrice.setTimestamp(LocalDateTime.now());


            //1. Cache in redis
//            redisTemplate.opsForValue().set(
//                    PRICE_KEY_PREFIX+symbol,currentPrice
//
//            );


            //2. Broadcast via websocket -> all customers

            /*
             *
             *
             *
             *
             *
            What happens on convertAndSend("/topic/prices/AAPL", currentPrice)?
            *
            *
            *
            * The template converts StockPrice to JSON.
                The simple broker finds every browser subscribed to exactly /topic/prices/AAPL.
                    It writes a MESSAGE frame to each of their sockets. If nobody is subscribed, nothing is sent.
                    *
                    *
                    *
                    *
                    *
                    * The browser connects and subscribes once. After that,
                    * every tick is pushed to it on the same open connection, with no polling.
             */

            simpMessagingTemplate
                    .convertAndSend("/topic/prices/" + symbol, currentPrice);


            // Publish To Kafka
            Map<String, Object> priceUpdateEvent = new HashMap<>();
            priceUpdateEvent.put("symbol", symbol);
            priceUpdateEvent.put("price", newPrice);
            priceUpdateEvent.put("timestamp", LocalDateTime.now());


            kafkaTemplate.send(PRICE_UPDATED_TOPIC, symbol, priceUpdateEvent);

        });


        log.debug("Prices Updated For {} Stocks", stockPrices.size());


        //1.1 Cache in redis
        

        /*

        Makes one call to Redis per stock,
         so 50 stocks means 50 separate requests and responses every tick.
         o make that one round trip, pipeline them:

         */
        // SessionCallback (not RedisCallback): it binds the connection to this thread,
        // so the redisTemplate calls below go into one pipeline = one network round trip.
        redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) {
                stockPrices.forEach((symbol, stockPrice) -> redisTemplate.opsForValue().set(
                        PRICE_KEY_PREFIX + symbol, stockPrice, Duration.ofMinutes(1)));
                return null;
            }
        });

    }



    /*
     * Get Current Price
     *
     * Reads the in-memory map, not Redis: the map is the source Redis is written from,
     * so it is always as fresh or fresher, with no network round trip.
     * Redis is for OTHER services, which don't have this map.
     */

    public StockPrice getPrice(String symbol) {
        StockPrice price = stockPrices.get(symbol.toUpperCase());
        if (price == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found: " + symbol);
        }
        return price;
    }



    /*
        Get All Active Stock Prices
     */


    public List<StockPrice> getAllPrices() {
        return stockPrices.values().stream().toList();
    }



    /*
        Get All  Stock s
     */

    public List<Stock> getAllStocks() {
        return stockRepository.findByActiveTrue();
    }


}
