package com.trading.marketdataservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;





/*
* 1. Client Connects to /ws endpoint
* 2. Client subscribes to /topic/prices/{symbol}
* 3.Server Publishes price updates to that topic
* 4.Client receives updated in real time - no polling needed
*
*
*
* This is exactly how Zeroha/Groww stream real time prices
 */

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // server -> clients: subscribe to /topic/prices/{symbol}
        registry.enableSimpleBroker("/topic");
        // clients -> server: messages sent to /app/** go to @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ponytail: any origin is fine in dev; restrict to the frontend's origin before prod
        registry.addEndpoint("/ws").setAllowedOriginPatterns("*").withSockJS();
    }
}
