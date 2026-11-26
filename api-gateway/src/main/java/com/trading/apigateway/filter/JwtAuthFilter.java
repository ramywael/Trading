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
// reactive version: WebFlux requests are org.springframework.http.server.reactive.ServerHttpRequest,
// the servlet org.springframework.http.server.ServerHttpRequest would fail with ClassCastException
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.util.List;


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

            ServerHttpRequest request = exchange.getRequest();
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

                // A refresh token (7 days) must not work as an access token
                if (!"access".equals(claims.get("type", String.class))) {
                    return unauthorizedResponse(exchange, "Not an access token");
                }

                // user-service puts the user id in "sub" and the email in an "email" claim
                String userId = claims.getSubject();
                String email = claims.get("email", String.class);

                log.debug("userId: {} email:{}", userId, email);

                // remove first: never trust an X-User-Id the client sent
                ServerHttpRequest modifiedRequest = request.mutate()
                        .headers(h -> {
                            h.remove("X-User-Id");
                            h.set("X-User-Id", userId);
                        })
                        .build();

                return chain.filter(exchange.mutate().request(modifiedRequest).build());


            } catch (Exception e) {
                log.info("Invalid JWT Token: {}", e.getMessage());
                return unauthorizedResponse(exchange, "Invalid JWT Token Or Expired");
            }

        };

    }

    // Exact paths / prefixes, not contains(): with contains, "/api/v1/orders/login-history"
    // would also skip the JWT check.
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/v1/users/register",
            "/api/v1/users/login");
    private static final List<String> PUBLIC_PREFIXES = List.of(
            "/actuator/health",
            "/ws/");

    private boolean isPublicEndpoint(String path) {
        return PUBLIC_PATHS.contains(path)
                || PUBLIC_PREFIXES.stream().anyMatch(path::startsWith);
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
