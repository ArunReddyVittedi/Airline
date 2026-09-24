package com.telusko.airline.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    private final Key key;
    private final String issuer;
    private final long accessMillis;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.issuer}") String issuer,
                      @Value("${security.jwt.access-minutes}") long accessMinutes) {
        // HS256 needs at least 32 bytes of key material. A shorter secret throws here, at
        // startup, rather than on the first login, which is the better place to find out.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessMillis = accessMinutes * 60_000;
    }

    public String issue(String email, String role) {
        Date now = new Date();
        return Jwts.builder()
                .setSubject(email)
                .claim("role", role)
                .setIssuer(issuer)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + accessMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Returns the email in the token, or null if the token is not valid. */
    public String emailOf(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .requireIssuer(issuer)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            return claims.getSubject();
        } catch (RuntimeException ex) {
            // Expired, tampered with, or simply not a JWT. All of them mean the same thing
            // to the caller, and the filter turns a null into an anonymous request.
            return null;
        }
    }
}
