package com.trading.userservice.controller;

import com.trading.userservice.dto.AuthResponse;
import com.trading.userservice.dto.LoginRequest;
import com.trading.userservice.dto.RegisterRequest;
import com.trading.userservice.dto.UserResponse;
import com.trading.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
@Slf4j
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid  @RequestBody RegisterRequest registerRequest)
    {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.register(registerRequest));
    }





    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid  @RequestBody LoginRequest loginRequest)
    {
        return ResponseEntity.ok().body(userService.login(loginRequest));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile(@RequestHeader("X-USER-Id") String userId)
    {
        return ResponseEntity.ok(userService.getUserById(userId));
    }



    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String userId){
        return ResponseEntity.ok(userService.getUserById(userId));
    }
    @PostMapping("/{userId}/funds/add")
    public ResponseEntity<UserResponse> addFunds(
            @PathVariable String userId,
            @RequestParam BigDecimal amount
    )
    {
        return  ResponseEntity.ok(userService.addFunds(userId,amount));
    }


    @PostMapping("/{userId}/funds/deduct")
    public ResponseEntity<UserResponse> deductMoney(
            @PathVariable String userId,
            @RequestParam BigDecimal amount
    ){
        return  ResponseEntity.ok(userService.deductMoney(userId,amount));
    }



    @PostMapping("/{userId}/funds/credit")
    public ResponseEntity<UserResponse> creditFunds(
            @PathVariable String userId,
            @RequestParam BigDecimal amount
    ){
        return  ResponseEntity.ok(userService.creditFunds(userId,amount));
    }





}
