package com.trading.userservice.entity;


import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
@Entity
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "email",nullable = false,unique = true)
    private String email;

    @Column(nullable = false)
    private String firstName;
    @Column(nullable = false)
    private String lastName;

    @Column(name = "password",nullable = false)
    private String password;

    @Column(nullable = false,precision = 15,scale = 2)
    private BigDecimal walletBalance=BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    private UserStatus userStatus=UserStatus.Activated;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
