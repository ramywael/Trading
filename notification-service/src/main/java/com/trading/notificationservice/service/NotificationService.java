package com.trading.notificationservice.service;


import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@AllArgsConstructor
@Slf4j
public class NotificationService {


    @KafkaListener(topics = "order.executed")
    public void consumeOrderExecuted(@Payload Map<String, Object> payLoad) {

        try {
            String type = (String) payLoad.get("type");
            String userId = (String) payLoad.get("userId");
            String symbol = (String) payLoad.get("symbol");
            Object price = payLoad.get("price");
            Object quantity = payLoad.get("quantity");
            Object totalAmount = payLoad.get("totalAmount");


            sendMessage(userId
                    , type.equalsIgnoreCase("buy") ? "ORDER-EXECUTED-BUY" : "ORDER-EXECUTED-SELL"
                    , String.format("%s %s shares of %s at %s. Total %s", type, quantity, symbol, price, totalAmount));


        } catch (Exception ex) {
            log.error("Error : {}", ex.getMessage());
        }


    }


    @KafkaListener(topics = "order.failed")
    public void consumeOrderFailed(@Payload Map<String, Object> payLoad) {
        try {
            String userId = (String) payLoad.get("userId");
            String symbol = (String) payLoad.get("symbol");
            String reason = (String) payLoad.get("reason");

            sendMessage(userId, "Order Failed", String.format("Your Order for %s failed. Reason: %s", symbol, reason));
        } catch (Exception ex) {
            log.error("Error : {}", ex.getMessage());

        }
    }


    @KafkaListener(topics = "user.registered")
    public void consumeUserRegistered(@Payload Map<String,Object> payLoad){
        try {
            String userId = (String) payLoad.get("userId");
            String firstName = (String) payLoad.get("firstName");
            Object walletBalance=payLoad.get("walletBalance");


            sendMessage(userId,"Welcome To Stock Trading Platform",String.format("Welcome %s! %s added to your balance",firstName,walletBalance));

        } catch (Exception ex) {
            log.error("Error : {}", ex.getMessage());

        }
    }

    private void sendMessage(String userId, String title, String message) {

        log.info("------------------------------------------------");
        log.info("NOTIFICATION SENT");
        log.info("TO USER: {}",userId);
        log.info("Title: {}",title);
        log.info("Message: {}",message);
        log.info("------------------------------------------------");

    }
}
