package com.trading.portfolioservice.entity;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "holdings",uniqueConstraints = @UniqueConstraint(columnNames ={"user_id","symbol"} ))
public class Holding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false,name = "user_id")
    private  String userId;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String companyName;

    private int quantity;

    @Column(precision = 15,scale = 2)
    private BigDecimal averageBuyPrice;

    @Column(precision = 15,scale = 2)
    private BigDecimal totalInvested;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
