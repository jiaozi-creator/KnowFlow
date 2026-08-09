package com.knowflow.security;

import com.knowflow.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {
    private final AppProperties.Jwt properties;
    private final SecretKey key;

    public JwtService(AppProperties.Jwt properties) {
        this.properties = properties;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public String createAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(principal.userId().toString())
                .claim("tenantId", principal.tenantId())
                .claim("email", principal.email())
                .claim("role", principal.role())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtl())))
                .signWith(key)
                .compact();
    }

    public UserPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return new UserPrincipal(
                Long.parseLong(claims.getSubject()),
                claims.get("tenantId", Long.class),
                claims.get("email", String.class),
                claims.get("role", String.class)
        );
    }

    public long accessTokenSeconds() { return properties.accessTokenTtl().toSeconds(); }
}
