package com.trading.orderservice.dto;

import com.trading.orderservice.entity.OrderType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {

    private String userId;

    @NotBlank(message = "Stock Symbol is required")
    private String symbol;
    @NotBlank(message = "Order Type is required -BUY or SELL")
    private OrderType orderType;
    @Min(value = 1,message = "Quantity Must Be at least 1")
    private int quantity;

}
