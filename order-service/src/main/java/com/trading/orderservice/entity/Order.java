package com.trading.orderservice.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.CreatedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable=false)
    private String userId;

    @Column(nullable=false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private OrderType orderType;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private OrderStatus orderStatus;

    @Column(nullable = false,precision = 15,scale = 2)
    private BigDecimal price;

    @Column(nullable = false,precision = 15,scale = 2)

    private BigDecimal totalAmount;

    @Column(nullable = false)
    private int quantity;

    private LocalDateTime executedAt;

    @CreationTimestamp
    private LocalDateTime createdAt;

    private String failedReason;

}
