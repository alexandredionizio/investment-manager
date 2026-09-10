package com.investmanager.api.auth.security;

import com.investmanager.api.user.entity.User;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.util.Date;

@Service
public class JwtService {

    private final String secret;
    private final long expiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {

        this.secret = secret;
        this.expiration = expiration;
    }

    private SecretKey getSigningKey() {

        byte[] keyBytes = Decoders.BASE64.decode(secret);

        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(User user) {
    Date now = new Date();

    Date expirationDate =
            new Date(now.getTime() + expiration);

    return Jwts.builder()
            .subject(user.getId().toString())
            .issuedAt(now)
            .expiration(expirationDate)
            .signWith(getSigningKey())
            .compact();
    }

    public String extractSubject(String token) {

        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean isTokenValid(String token) {
        try {
            extractSubject(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
