package com.example.dynamicform.security;

import com.example.dynamicform.security.SecurityRepository.UserRow;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TokenService {
    private final JwtEncoder jwtEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.security.access-token-minutes:15}")
    private long accessTokenMinutes;

    @Value("${app.security.refresh-token-days:7}")
    private long refreshTokenDays;

    public AccessToken issueAccessToken(UserRow user, Set<String> roles, Set<String> functions) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(accessTokenMinutes));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("dynamic-form-api")
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.username())
                .claim("uid", user.id().toString())
                .claim("roles", roles)
                .claim("functions", functions)
                .claim("mustChangePassword", user.mustChangePassword())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, Duration.between(now, expiresAt).toSeconds());
    }

    public String newRefreshToken() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashRefreshToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(Duration.ofDays(refreshTokenDays));
    }

    public record AccessToken(String value, long expiresIn) {}
}
