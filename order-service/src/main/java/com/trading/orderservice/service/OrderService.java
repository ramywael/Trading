package com.trading.orderservice.service;


import com.trading.orderservice.client.MarketDataClient;
import com.trading.orderservice.client.UserServiceClient;
import com.trading.orderservice.dto.OrderRequest;
import com.trading.orderservice.entity.Order;
import com.trading.orderservice.entity.OrderStatus;
import com.trading.orderservice.entity.OrderType;
import com.trading.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private OrderRepository orderRepository;
    private final MarketDataClient marketDataClient;
    private final UserServiceClient userServiceClient;

    private static final String ORDER_EXECUTED_TOPIC="order.executed";
    private static final String ORDER_FAILED_TOPIC="order.failed";


    /*
     * Place a Buy Or Sell Order
     *
     * Flow
     * 1. Get Current Price From Market
     * 2. Will Calculate total amount
     * 3. Execute
     * 4.Public Order Event TO kAFKA
     *
     */


    public Order placeOrder(OrderRequest orderRequest) {
        log.info("Placing {} order : {} x {} for user : {}", orderRequest.getOrderType(), orderRequest.getQuantity(), orderRequest.getSymbol(), orderRequest.getUserId());


        //STEP 1
        Map<String,Object> priceDate =marketDataClient.getStockPrice(orderRequest.getSymbol());

        BigDecimal currentPrice = new BigDecimal(
                priceDate.get("price").toString()
        );


        //Step 2
        BigDecimal totalAmount = currentPrice.multiply(
                BigDecimal.valueOf(orderRequest.getQuantity())
        );



        // Create Order

        Order order = new Order();
        order.setUserId(orderRequest.getUserId());
        order.setSymbol(orderRequest.getSymbol());
        order.setOrderType(orderRequest.getOrderType());
        order.setQuantity(orderRequest.getQuantity());
        order.setPrice(currentPrice);
        order.setTotalAmount(totalAmount);
        order.setOrderStatus(OrderStatus.PENDING);


        //save order
        Order savedOrder =orderRepository.save(order);

        log.info("Order Created {}",savedOrder.getId());

        try{
            if(orderRequest.getOrderType() == OrderType.BUY)
            {
                userServiceClient.deductMoney(orderRequest.getUserId(),totalAmount);
            }else{
                userServiceClient.creditFunds(orderRequest.getUserId(),totalAmount);
            }

            savedOrder.setOrderStatus(OrderStatus.EXECUTED);
            savedOrder.setExecutedAt(LocalDateTime.now());

            orderRepository.save(savedOrder);

            log.info("Order EXECUTED : {} {} x {} at {}",orderRequest.getOrderType(),orderRequest.getQuantity(),orderRequest.getSymbol(),currentPrice);



            // Publish
            publishOrderEvent(ORDER_EXECUTED_TOPIC,savedOrder,null);


        } catch (Exception e) {
            savedOrder.setOrderStatus(OrderStatus.FAILED);
            savedOrder.setFailedReason(e.getMessage());
            orderRepository.save(savedOrder);

            log.error("Order FAILED : {} reason: {}",savedOrder.getId(),savedOrder.getFailedReason());

            publishOrderEvent(ORDER_FAILED_TOPIC,savedOrder,e.getMessage());

        }


    }

    private void publishOrderEvent(String topic,Order order,String failedReason){

        Map<String,Object> orderEvent = new HashMap<>();
        orderEvent.put("orderId",order.getId());


    }
}
