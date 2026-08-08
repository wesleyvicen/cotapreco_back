package br.com.cotapreco.service;

import br.com.cotapreco.dto.AutenticacaoDtos.*;
import br.com.cotapreco.enums.PerfilUsuario;
import br.com.cotapreco.exception.RegraNegocioException;
import br.com.cotapreco.model.Empresa;
import br.com.cotapreco.model.TokenRedefinicaoSenhaUsuario;
import br.com.cotapreco.model.Usuario;
import br.com.cotapreco.repository.EmpresaRepository;
import br.com.cotapreco.repository.TokenRedefinicaoSenhaUsuarioRepository;
import br.com.cotapreco.repository.UsuarioRepository;
import br.com.cotapreco.security.ServicoJwt;
import br.com.cotapreco.helper.GeradorToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class AutenticacaoService {
    public record ResultadoLogin(RespostaLogin resposta, String refreshToken, Instant refreshExpiresAt) {}
    private final UsuarioRepository repository; private final EmpresaRepository empresas; private final PasswordEncoder passwordEncoder; private final ServicoJwt jwtService;
    private final TokenRedefinicaoSenhaUsuarioRepository repositorioTokens; private final GeradorToken geradorToken; private final EnvioEmailService envioEmailService;
    private final ServicoRefreshToken refreshTokens;
    @org.springframework.beans.factory.annotation.Value("${app.frontend-public-url}") private String urlFrontend;
    public ResultadoLogin login(SolicitacaoLogin request) {
        Usuario user = repository.findByEmailIgnoreCase(request.email().trim()).filter(Usuario::isAtivo)
            .orElseThrow(() -> new RegraNegocioException("E-mail ou senha inválidos."));
        if (!user.getEmpresa().isAtivo() || !passwordEncoder.matches(request.password(), user.getSenhaHash()))
            throw new RegraNegocioException("E-mail ou senha inválidos.");
        return respostaLogin(user);
    }
    @Transactional public ResultadoLogin cadastrarFarmacia(SolicitacaoCadastroFarmacia request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String cnpj = request.cnpj().replaceAll("\\D", "");
        if (repository.existsByEmailIgnoreCase(email)) throw new RegraNegocioException("Este e-mail já está cadastrado. Faça login para continuar.");
        if (empresas.existsByCnpj(cnpj)) throw new RegraNegocioException("Este CNPJ já está cadastrado em outra farmácia.");
        Empresa empresa = new Empresa();
        empresa.setNome(request.nomeFarmacia().trim()); empresa.setCnpj(cnpj); empresa.setSlug(gerarSlug(request.nomeFarmacia()));
        empresas.save(empresa);
        Usuario usuario = new Usuario();
        usuario.setEmpresa(empresa); usuario.setNome(request.nomeUsuario().trim()); usuario.setEmail(email);
        usuario.setSenhaHash(passwordEncoder.encode(request.senha())); usuario.setPerfil(PerfilUsuario.ADMIN);
        repository.save(usuario);
        return respostaLogin(usuario);
    }
    public ResultadoLogin renovar(String refreshToken) { ServicoRefreshToken.TokenUsuario renovacao=refreshTokens.rotacionarUsuario(refreshToken);return respostaLogin(renovacao.usuario(),renovacao.token()); }
    @Transactional public void sair(String refreshToken) { refreshTokens.revogarUsuario(refreshToken); }
    @Transactional
    public void alterarSenha(Usuario usuario, SolicitacaoAlteracaoSenha solicitacao) {
        if (!passwordEncoder.matches(solicitacao.senhaAtual(), usuario.getSenhaHash()))
            throw new RegraNegocioException("A senha atual está incorreta.");
        if (passwordEncoder.matches(solicitacao.novaSenha(), usuario.getSenhaHash()))
            throw new RegraNegocioException("A nova senha deve ser diferente da senha atual.");
        usuario.setSenhaHash(passwordEncoder.encode(solicitacao.novaSenha()));
        usuario.setVersaoAutenticacao(usuario.getVersaoAutenticacao() + 1);
        refreshTokens.revogarTodos(usuario);
        repository.save(usuario);
    }
    @Transactional
    public RespostaMensagem solicitarRedefinicao(SolicitacaoEsqueciSenha solicitacao) {
        String email = solicitacao.email().trim().toLowerCase(Locale.ROOT);
        repository.findByEmailIgnoreCase(email).filter(usuario -> usuario.isAtivo() && usuario.getEmpresa().isAtivo()).ifPresent(usuario -> {
            String token = geradorToken.generate();
            repositorioTokens.deleteByUsuarioId(usuario.getId());
            TokenRedefinicaoSenhaUsuario registro = new TokenRedefinicaoSenhaUsuario();
            registro.setUsuario(usuario);
            registro.setTokenHash(hash(token));
            registro.setExpiraEm(Instant.now().plus(30, ChronoUnit.MINUTES));
            repositorioTokens.save(registro);
            String link = baseUrlFrontend() + "/redefinir-senha?token=" + token;
            envioEmailService.enviarRedefinicaoSenhaUsuario(usuario.getEmail(), usuario.getNome(), link);
        });
        return new RespostaMensagem("Se o e-mail estiver cadastrado, enviaremos as instruções para redefinir a senha.");
    }
    @Transactional
    public RespostaMensagem redefinirSenha(SolicitacaoRedefinicaoSenha solicitacao) {
        TokenRedefinicaoSenhaUsuario registro = repositorioTokens.findByTokenHash(hash(solicitacao.token()))
            .orElseThrow(() -> new RegraNegocioException("O link de redefinição é inválido ou expirou."));
        if (!registro.valido()) throw new RegraNegocioException("O link de redefinição é inválido ou expirou.");
        Usuario usuario = registro.getUsuario();
        if (!usuario.isAtivo() || !usuario.getEmpresa().isAtivo())
            throw new RegraNegocioException("O link de redefinição é inválido ou expirou.");
        if (passwordEncoder.matches(solicitacao.novaSenha(), usuario.getSenhaHash()))
            throw new RegraNegocioException("A nova senha deve ser diferente da senha atual.");
        usuario.setSenhaHash(passwordEncoder.encode(solicitacao.novaSenha()));
        usuario.setVersaoAutenticacao(usuario.getVersaoAutenticacao() + 1);
        refreshTokens.revogarTodos(usuario);
        registro.setUtilizadoEm(Instant.now());
        return new RespostaMensagem("Senha redefinida com sucesso. Você já pode entrar.");
    }
    public VisaoUsuario view(Usuario user) { return new VisaoUsuario(user.getId(), user.getNome(), user.getEmail(), user.getPerfil(), user.getEmpresa().getId(), user.getEmpresa().getNome()); }
    private ResultadoLogin respostaLogin(Usuario usuario) { return respostaLogin(usuario, refreshTokens.criar(usuario)); }
    private ResultadoLogin respostaLogin(Usuario usuario, ServicoRefreshToken.TokenEmitido refreshToken) { return new ResultadoLogin(new RespostaLogin(jwtService.generate(usuario), "Bearer", jwtService.expirationSeconds(), view(usuario)), refreshToken.valor(), refreshToken.expiraEm()); }
    private String gerarSlug(String nome) {
        String base = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return (base.isBlank() ? "farmacia" : base.substring(0, Math.min(base.length(), 120))) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
    private String hash(String valor) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(valor.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException("Não foi possível proteger o token.", ex); }
    }
    private String baseUrlFrontend() { return urlFrontend.replaceAll("/+$", ""); }
}
