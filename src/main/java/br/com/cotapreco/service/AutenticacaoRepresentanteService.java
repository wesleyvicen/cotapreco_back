package br.com.cotapreco.service;

import br.com.cotapreco.dto.RepresentanteDtos.*;
import br.com.cotapreco.exception.RegraNegocioException;
import br.com.cotapreco.helper.GeradorToken;
import br.com.cotapreco.model.*;
import br.com.cotapreco.repository.*;
import br.com.cotapreco.security.ServicoJwt;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;

@Service @RequiredArgsConstructor
public class AutenticacaoRepresentanteService {
    private final RepresentanteRepository repositorio;
    private final TokenRedefinicaoSenhaRepresentanteRepository repositorioTokens;
    private final CotacaoRepository repositorioCotacoes;
    private final PasswordEncoder codificadorSenha;
    private final ServicoJwt servicoJwt;
    private final GeradorToken geradorToken;
    private final EnvioEmailService envioEmailService;
    @Value("${app.frontend-url}") private String urlFrontend;

    @Transactional
    public RespostaAutenticacaoRepresentante cadastrar(SolicitacaoCadastroRepresentante solicitacao) {
        Cotacao cotacao = repositorioCotacoes.findByTokenPublico(solicitacao.tokenCotacao())
            .orElseThrow(() -> new RegraNegocioException("Cotação não encontrada."));
        if (!cotacao.podeReceberRespostas()) throw new RegraNegocioException("Esta cotação não está aberta para cadastro e respostas.");
        String telefone = normalizarTelefone(solicitacao.telefone());
        String email = normalizarEmail(solicitacao.email());
        validarSenha(solicitacao.senha());
        if (repositorio.existsByTelefone(telefone)) throw new RegraNegocioException("Este telefone já possui uma conta.");
        if (repositorio.existsByEmailIgnoreCase(email)) throw new RegraNegocioException("Este e-mail já possui uma conta.");
        Representante representante = new Representante();
        representante.setNome(solicitacao.nome().trim());
        representante.setTelefone(telefone);
        representante.setEmail(email);
        representante.setSenhaHash(codificadorSenha.encode(solicitacao.senha()));
        representante.setUltimoLoginEm(Instant.now());
        try { repositorio.saveAndFlush(representante); }
        catch (DataIntegrityViolationException ex) { throw new RegraNegocioException("Telefone ou e-mail já cadastrado."); }
        return respostaAutenticacao(representante);
    }

    @Transactional
    public RespostaAutenticacaoRepresentante entrar(SolicitacaoLoginRepresentante solicitacao) {
        String telefone = normalizarTelefone(solicitacao.telefone());
        Representante representante = repositorio.findByTelefone(telefone).filter(Representante::isAtivo)
            .orElseThrow(() -> new RegraNegocioException("Telefone ou senha inválidos."));
        if (!codificadorSenha.matches(solicitacao.senha(), representante.getSenhaHash()))
            throw new RegraNegocioException("Telefone ou senha inválidos.");
        representante.setUltimoLoginEm(Instant.now());
        return respostaAutenticacao(representante);
    }

    @Transactional(readOnly = true)
    public VisaoRepresentante visualizar(Representante representante) {
        return new VisaoRepresentante(representante.getId(), representante.getNome(), representante.getTelefone(), representante.getEmail());
    }

    @Transactional
    public MensagemRepresentante solicitarRedefinicao(SolicitacaoEsqueciSenha solicitacao) {
        repositorio.findByEmailIgnoreCase(normalizarEmail(solicitacao.email())).filter(Representante::isAtivo).ifPresent(representante -> {
            String token = geradorToken.generate();
            TokenRedefinicaoSenhaRepresentante registro = new TokenRedefinicaoSenhaRepresentante();
            registro.setRepresentante(representante);
            registro.setTokenHash(hash(token));
            registro.setExpiraEm(Instant.now().plus(30, ChronoUnit.MINUTES));
            repositorioTokens.save(registro);
            String link = urlFrontend + "/representante/redefinir-senha?token=" + token;
            envioEmailService.enviarRedefinicaoSenhaRepresentante(representante.getEmail(), representante.getNome(), link);
        });
        return new MensagemRepresentante("Se o e-mail estiver cadastrado, enviaremos as instruções para redefinir a senha.");
    }

    @Transactional
    public MensagemRepresentante redefinirSenha(SolicitacaoRedefinicaoSenha solicitacao) {
        validarSenha(solicitacao.novaSenha());
        TokenRedefinicaoSenhaRepresentante registro = repositorioTokens.findByTokenHash(hash(solicitacao.token()))
            .orElseThrow(() -> new RegraNegocioException("O link de redefinição é inválido ou expirou."));
        if (!registro.valido()) throw new RegraNegocioException("O link de redefinição é inválido ou expirou.");
        Representante representante = registro.getRepresentante();
        representante.setSenhaHash(codificadorSenha.encode(solicitacao.novaSenha()));
        representante.setVersaoAutenticacao(representante.getVersaoAutenticacao() + 1);
        registro.setUtilizadoEm(Instant.now());
        return new MensagemRepresentante("Senha redefinida com sucesso. Você já pode entrar.");
    }

    public static String normalizarTelefone(String valor) {
        String numeros = valor == null ? "" : valor.replaceAll("\\D", "");
        if ((numeros.length() == 12 || numeros.length() == 13) && numeros.startsWith("55")) numeros = numeros.substring(2);
        if (numeros.length() != 10 && numeros.length() != 11) throw new RegraNegocioException("Informe um telefone brasileiro com DDD.");
        return numeros;
    }

    private RespostaAutenticacaoRepresentante respostaAutenticacao(Representante representante) {
        return new RespostaAutenticacaoRepresentante(servicoJwt.gerar(representante), "Bearer", servicoJwt.expiracaoSegundos(), visualizar(representante));
    }
    private String normalizarEmail(String valor) { return valor.trim().toLowerCase(Locale.ROOT); }
    private void validarSenha(String senha) {
        if (senha == null || senha.length() < 8 || senha.length() > 72 || !senha.matches(".*[A-Za-zÀ-ÿ].*") || !senha.matches(".*\\d.*"))
            throw new RegraNegocioException("A senha deve ter entre 8 e 72 caracteres, com pelo menos uma letra e um número.");
    }
    private String hash(String valor) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("Não foi possível proteger o token.", ex); }
    }
}
