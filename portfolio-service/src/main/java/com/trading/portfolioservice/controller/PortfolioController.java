package com.trading.portfolioservice.controller;


import com.trading.portfolioservice.service.PortfolioService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/portfolio")
@AllArgsConstructor
public class PortfolioController {

    private PortfolioService portfolioService;


    @GetMapping("/me")
    public ResponseEntity<Map<String,Object>> getMyPortfolio(@RequestHeader("X-User-Id")  String userId){

        return ResponseEntity.ok(PortfolioService.getUserPortfolio(userId));
    }



    /*
    * Get Portfolio For the Internal Calls
    *
    *
     */

    @GetMapping("/{userId}")
    public ResponseEntity<Map<String,Object>> getMyPortfolio(@PathVariable String userId){

        return ResponseEntity.ok(PortfolioService.getUserPortfolio(userId));
    }



}
