package br.com.cotapreco.security;

import br.com.cotapreco.model.Usuario;
import br.com.cotapreco.model.Representante;
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
        return Jwts.builder().subject(user.getEmail()).claim("role", user.getPerfil().name()).claim("tipo", "FARMACIA")
            .claim("versao", user.getVersaoAutenticacao())
            .issuedAt(Date.from(now)).expiration(Date.from(now.plus(expirationMinutes, java.time.temporal.ChronoUnit.MINUTES)))
            .signWith(key).compact();
    }
    public String gerar(Representante representante) {
        Instant agora = Instant.now();
        return Jwts.builder().subject(representante.getTelefone()).claim("role", "REPRESENTANTE")
            .claim("tipo", "REPRESENTANTE").claim("versao", representante.getVersaoAutenticacao())
            .issuedAt(Date.from(agora)).expiration(Date.from(agora.plus(expirationMinutes, java.time.temporal.ChronoUnit.MINUTES)))
            .signWith(key).compact();
    }
    public Claims extrairDados(String token) { return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload(); }
    public String extractSubject(String token) { return extrairDados(token).getSubject(); }
    public String extrairTipo(String token) { return extrairDados(token).get("tipo", String.class); }
    public int extrairVersao(String token) { Integer versao = extrairDados(token).get("versao", Integer.class); return versao == null ? 0 : versao; }
    public boolean isValid(String token) { try { extrairDados(token); return true; } catch (JwtException | IllegalArgumentException ex) { return false; } }
    public long expirationSeconds() { return expirationMinutes * 60; }
    public long expiracaoSegundos() { return expirationSeconds(); }
}
