package com.trading.apigateway.filter;


import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;


@Component
@Slf4j
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {


    public JwtAuthFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {

            ServerHttpRequest request = (ServerHttpRequest) exchange.getRequest();
            String path = request.getURI().getPath();

            //Skiipped JWT For Public Endpoints
            if (isPublicEndpoint(path)) {
                log.debug("Public Endoint - Skipping Jwt: {}", path);
                return chain.filter(exchange);
            }

            if (!request.getHeaders().containsHeader("Authorization")) {
                log.debug("Missing Authorization header: {}", path);
                return unauthorizedResponse(exchange, "Missing Authorization header");
            }

            String authHeader = request.getHeaders().getFirst("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                return unauthorizedResponse(exchange, "Invalid Authorization format");
            }

            String token = authHeader.substring(7);
            try {
                Claims claims = extractClaims(token);

                String userId = claims.get("userId", String.class);

                String email = claims.getSubject();

                log.debug("userId: {} email:{}", userId, email);

                ServerHttpRequest modifiedRequest = (ServerHttpRequest) exchange.getRequest().mutate()
                        .headers(h -> {
                            h.remove("X-User-Id");
                            h.set("X-User-Id", claims.getSubject());
                        })
                        .build();


            } catch (Exception e) {

            }

        }

    }


    public static class Config {

    }
}
