package com.trading.apigateway.filter;


import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.security.PublicKey;


@Component
@Slf4j
public class JwtAuthFilter extends AbstractGatewayFilterFactory<JwtAuthFilter.Config> {


    public JwtAuthFilter() {
        super(Config.class);
    }


    @Value("${jwt.secret}")
    private String jwtSecret;

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

                return chain.filter(exchange.mutate().request((org.springframework.http.server.reactive.ServerHttpRequest) modifiedRequest).build());


            } catch (Exception e) {
                log.info("Invalid JWT Token: {}", e.getMessage());
                return unauthorizedResponse(exchange, "Invalid JWT Token Or Expired");
            }

        };

    }

    private boolean isPublicEndpoint(String path) {
        return path.contains("/register")
                || path.contains("/login")
                || path.contains("health")
                || path.contains("/ws");
    }

    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {

        log.warn("Unauthorized: {}", message);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        return exchange.getResponse().setComplete();

    }

    /*
     * Verifies the token and returns what's inside it (the claims).
     * It throws if anything is wrong, so if it returns, the token can be trusted.
     *
     * jjwt 0.12 names (older tutorials use the 0.11 ones):
     *   setSigningKey(key) -> verifyWith(key)          only accepts a real key type, not a String
     *   parseClaimsJws(t)  -> parseSignedClaims(t)     (parseContentJws is for raw-byte payloads,
     *                                                   it would throw on our claims tokens)
     *   getBody()          -> getPayload()
     */
    private Claims extractClaims(String token) {
        return Jwts.parser()                  // 1. start building a parser
                .verifyWith(getSignKey())     // 2. the key that checks the signature (same secret user-service signs with)
                .build()                      // 3. build the parser (immutable, thread-safe)
                .parseSignedClaims(token)     // 4. split header.payload.signature, check the signature
                                              //    (SignatureException if the payload was edited),
                                              //    check exp (ExpiredJwtException if expired)
                .getPayload();                // 5. the claims: sub (userId), email, type, iat, exp
    }


    /*
     * Turns the Base64 secret from application.yaml into an HMAC key.
     * Same code as signingKey() in user-service: one side signs with it, the other verifies.
     * The key length picks the algorithm: 32+ bytes = HS256, 48+ = HS384, 64+ = HS512.
     */
    private SecretKey getSignKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }


    public static class Config {

    }
}
