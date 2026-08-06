package br.com.cotapreco.security;

import br.com.cotapreco.model.Usuario;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Date;

@Service
public class ServicoJwt {
    private final SecretKey key;
    private final long expirationMinutes;
    public ServicoJwt(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMinutes = expirationMinutes;
    }
    public String generate(Usuario user) {
        Instant now = Instant.now();
        return Jwts.builder().subject(user.getEmail()).claim("role", user.getPerfil().name())
            .issuedAt(Date.from(now)).expiration(Date.from(now.plus(expirationMinutes, java.time.temporal.ChronoUnit.MINUTES)))
            .signWith(key).compact();
    }
    public String extractSubject(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject(); }
    public boolean isValid(String token) { try { extractSubject(token); return true; } catch (JwtException | IllegalArgumentException ex) { return false; } }
    public long expirationSeconds() { return expirationMinutes * 60; }
}
