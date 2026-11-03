package com.trading.orderservice.controller;


import com.trading.orderservice.dto.OrderRequest;
import com.trading.orderservice.entity.Order;
import com.trading.orderservice.service.OrderService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@AllArgsConstructor
@RequestMapping("/api/v1/orders")
@Slf4j
public class OrderController {


    private final OrderService orderService;

    /*
      * Place OrderWhether it is Buy Or Sell
     */

    @PostMapping
    public ResponseEntity<Order> placeOrder
            (
                    @RequestHeader("X-USER-ID") String userId,
                    @Valid @RequestBody OrderRequest orderRequest
            ) {
        orderRequest.setUserId(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(orderService.placeOrder(request));

    }

    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId,@RequestHeader("X-User-Id") String userId){

        Order order = orderService.getOrder(orderId);

        //security Check

        if(!order.getUserId().equals(userId))
            return ResponseEntity.status(403).build();


        return ResponseEntity.ok(order);
    }




    @GetMapping("/my-orders")
    public ResponseEntity<List<Order>> getMyOrders(@RequestHeader("X-User-Id") String userId){

        List<Order> orderList = orderService.getMyOrders(userId);

        return ResponseEntity.ok(orderList);
    }



}
