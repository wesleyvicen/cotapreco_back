package br.com.cotapreco.service;

import br.com.cotapreco.exception.CredencialInvalidaException;
import br.com.cotapreco.helper.GeradorToken;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.TokenRefreshRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class ServicoRefreshToken {
    public record TokenEmitido(String valor, Instant expiraEm) {}
    public record TokenUsuario(TokenEmitido token, Usuario usuario) {}
    public record TokenRepresentante(TokenEmitido token, Representante representante) {}
    private final TokenRefreshRepository repositorio;
    private final GeradorToken gerador;
    @Value("${app.refresh-token.expiration-days:90}") private long diasExpiracao;

    @Transactional public TokenEmitido criar(Usuario usuario) { return criar(usuario, null, UUID.randomUUID().toString(), Instant.now().plus(diasExpiracao, ChronoUnit.DAYS)); }
    @Transactional public TokenEmitido criar(Representante representante) { return criar(null, representante, UUID.randomUUID().toString(), Instant.now().plus(diasExpiracao, ChronoUnit.DAYS)); }

    @Transactional(noRollbackFor = CredencialInvalidaException.class) public TokenUsuario rotacionarUsuario(String valor) {
        TokenRefresh atual = buscarAtivo(valor);
        if (atual.getUsuario() == null || !atual.getUsuario().isAtivo() || !atual.getUsuario().getEmpresa().isAtivo()) throw invalidar(atual);
        atual.setRevogadoEm(Instant.now());
        return new TokenUsuario(criar(atual.getUsuario(), null, atual.getFamilia(), atual.getExpiraEm()), atual.getUsuario());
    }

    @Transactional(noRollbackFor = CredencialInvalidaException.class) public TokenRepresentante rotacionarRepresentante(String valor) {
        TokenRefresh atual = buscarAtivo(valor);
        if (atual.getRepresentante() == null || !atual.getRepresentante().isAtivo()) throw invalidar(atual);
        atual.setRevogadoEm(Instant.now());
        return new TokenRepresentante(criar(null, atual.getRepresentante(), atual.getFamilia(), atual.getExpiraEm()), atual.getRepresentante());
    }

    @Transactional public void revogarUsuario(String valor) { revogar(valor, true); }
    @Transactional public void revogarRepresentante(String valor) { revogar(valor, false); }
    @Transactional public void revogarTodos(Usuario usuario) { repositorio.revogarPorUsuario(usuario.getId(), Instant.now()); }
    @Transactional public void revogarTodos(Representante representante) { repositorio.revogarPorRepresentante(representante.getId(), Instant.now()); }

    private TokenEmitido criar(Usuario usuario, Representante representante, String familia, Instant expiraEm) {
        String valor = gerador.generate();
        TokenRefresh token = new TokenRefresh();
        token.setUsuario(usuario); token.setRepresentante(representante); token.setTokenHash(hash(valor)); token.setFamilia(familia);
        token.setCriadoEm(Instant.now()); token.setExpiraEm(expiraEm); repositorio.save(token);
        return new TokenEmitido(valor, expiraEm);
    }
    private TokenRefresh buscarAtivo(String valor) {
        if (valor == null || valor.isBlank()) throw new CredencialInvalidaException();
        TokenRefresh token = repositorio.findByTokenHashForUpdate(hash(valor)).orElseThrow(CredencialInvalidaException::new);
        if (!token.ativo()) { if (token.getRevogadoEm() != null) repositorio.revogarFamilia(token.getFamilia(), Instant.now()); throw new CredencialInvalidaException(); }
        return token;
    }
    private CredencialInvalidaException invalidar(TokenRefresh token) { repositorio.revogarFamilia(token.getFamilia(), Instant.now()); return new CredencialInvalidaException(); }
    private void revogar(String valor, boolean usuario) {
        if (valor == null || valor.isBlank()) return;
        repositorio.findByTokenHashForUpdate(hash(valor)).ifPresent(token -> {
            if ((usuario && token.getUsuario() != null) || (!usuario && token.getRepresentante() != null)) token.setRevogadoEm(Instant.now());
        });
    }
    private String hash(String valor) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("Não foi possível proteger o token.", ex); }
    }
}
