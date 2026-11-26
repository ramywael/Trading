package com.trading.userservice.service;

import com.trading.userservice.dto.AuthResponse;
import com.trading.userservice.dto.LoginRequest;
import com.trading.userservice.dto.RegisterRequest;
import com.trading.userservice.dto.UserResponse;
import com.trading.userservice.entity.User;
import com.trading.userservice.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.SecretKey;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {


    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${jwt.refresh-expiration}")
    private Duration refreshExpiration;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-expiration}")
    private Duration accessExpiration;

    private static final String USER_REGISTERATION_TOPIC = "user.registered";

    public AuthResponse register(RegisterRequest registerRequest) {
        log.info("registering user :{}", registerRequest.getEmail());

        //Check If User Exists or not
        if (userRepository.existsByEmail(registerRequest.getEmail())) {
            throw new RuntimeException(
                    "Email Already Registered" + registerRequest.getEmail()
            );
        }

        User user = new User();
        user.setFirstName(registerRequest.getFirstName());
        user.setLastName(registerRequest.getLastName());
        user.setEmail(registerRequest.getEmail());
        user.setPassword(passwordEncoder.encode(registerRequest.getPassword()));
        user.setWalletBalance(
                registerRequest.getInitialDeposit() != null
                        ? registerRequest.getInitialDeposit()
                        : BigDecimal.valueOf(10000)
        );
        User savedUser = userRepository.save(user);

        log.info("User Registered {}", savedUser.getId());


        //Publish user Registered To Kafka

        Map<String, Object> userRegisteredEvent = new HashMap<>();
        userRegisteredEvent.put("userId", savedUser.getId());
        userRegisteredEvent.put("firstName", savedUser.getFirstName());
        userRegisteredEvent.put("lastName", savedUser.getLastName());
        userRegisteredEvent.put("email", savedUser.getEmail());
        userRegisteredEvent.put("walletBalance", savedUser.getWalletBalance());


        // Notify Kafka
        kafkaTemplate.send(USER_REGISTERATION_TOPIC, savedUser.getId(), userRegisteredEvent);


        String token = generateAccessToken(savedUser.getId(), savedUser.getEmail());


        String refreshToken = generateRefreshToken(savedUser.getId());


        return buildAuthResponse(savedUser, token, refreshToken);

    }


    /*
     * Swap a refresh token for a new access token + a new refresh token (rotation).
     * Public: the controller calls it from POST /api/v1/users/refresh.
     * Every failure is a 401, so the client knows to send the user back to login.
     */
    public AuthResponse refreshToken(String token) {

        // 1. Verify signature + expiry. Only the parsing is in try/catch,
        //    so the checks below keep their own messages.
        Claims claims;
        try {
            claims = parseToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        // 2. Only refresh tokens are accepted here (the gateway accepts only access tokens)
        if (!Objects.equals(claims.get("type", String.class), "refresh")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not a refresh token");
        }

        // 3. The refresh token has no email, so load the user (also blocks deleted users)
        User user = userRepository.findById(claims.getSubject())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        // 4. New pair of tokens
        String accessToken = generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = generateRefreshToken(user.getId());
        return buildAuthResponse(user, accessToken, refreshToken);
    }

    ///
    /// Build the signing key from the secret
    ///
    /// Decode the Base64 secret into bytes, then turn the bytes into an HMAC key:
    ///
    /// @return
    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }


    private Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }


    private String generateAccessToken(String userId, String email) {

        Instant now = Instant.now();
        Instant exp = now.plus(accessExpiration);


        return Jwts.builder()
                .subject(userId)
                .claim("email", email)
                .claim("type", "access")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey())
                .compact();

    }


    private String generateRefreshToken(String userId) {

        // Unique id for this token; it will be the Redis key that marks it as still valid
        String jti = UUID.randomUUID().toString();

        Instant now = Instant.now();
        Instant exp = now.plus(refreshExpiration);

        return Jwts.builder()
                .id(jti)                 // the "jti" claim; read back with claims.getId()
                .subject(userId)
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(signingKey())
                .compact();
    }




    /*
        add Funds To Wallet
     *
     *
     *
     *
     *
     */

    public UserResponse addFunds(String userId, BigDecimal amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found :" + userId));

        user.setWalletBalance(user.getWalletBalance().add(amount));

        User savedUser = userRepository.save(user);


        log.info("Funds Added : {} To User : {}", amount, userId);
        return mapToResponse(savedUser);

    }


    /*
        Credit funds to wallet - Called on Sell Order
      *
      *
      *
      *
      *
      *
      *
     */


    public UserResponse creditFunds(String userId, BigDecimal amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found :" + userId));

        user.setWalletBalance(user.getWalletBalance().add(amount));

        User savedUser = userRepository.save(user);


        log.info("Funds credited : {} To User : {}", amount, userId);
        return mapToResponse(savedUser);

    }


    /*
        Deduct finds from wallet - Called on Buy Order
      *
      *
      *
      *
      *
      *
     */


    public UserResponse deductMoney(String userId, BigDecimal amount) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found :" + userId));


        if (user.getWalletBalance().compareTo(amount) < 0) {
            throw new RuntimeException("Insufficient Wallet Balance");
        }


        user.setWalletBalance(user.getWalletBalance().subtract(amount));

        User savedUser = userRepository.save(user);


        log.info("Funds deducted : {} To User : {}", amount, userId);
        return mapToResponse(savedUser);
    }


    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        AuthResponse response = new AuthResponse();
        response.setUserId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setWalletBalance(user.getWalletBalance());
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshToken);
        return response;
    }


    public AuthResponse login(LoginRequest loginRequest) {
        log.info("login attempt : {}", loginRequest.getEmail());

        User user = userRepository.findByEmail(loginRequest.getEmail()).orElseThrow(
                () -> new RuntimeException("User Not Found" + loginRequest.getEmail()));


        if (!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid Credentials");
        }

        log.info("login successfully : {}", user.getId());


        String token = generateAccessToken(user.getId(), user.getEmail());


        String refreshToken = generateRefreshToken(user.getId());


        return buildAuthResponse(user, token, refreshToken);


    }


    /*
     * Get User By Id
     * Called By Other Services via feign (internal)
     *
     *
     */

    public UserResponse getUserById(String userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found :" + userId));

        return mapToResponse(user);

    }


    private UserResponse mapToResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setWalletBalance(user.getWalletBalance());
        response.setUserStatus(user.getUserStatus());

        return response;

    }

}
