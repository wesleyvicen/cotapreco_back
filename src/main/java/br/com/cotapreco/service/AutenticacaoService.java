package br.com.cotapreco.service;

import br.com.cotapreco.dto.AutenticacaoDtos.*;
import br.com.cotapreco.enums.PerfilUsuario;
import br.com.cotapreco.exception.RegraNegocioException;
import br.com.cotapreco.model.Empresa;
import br.com.cotapreco.model.Usuario;
import br.com.cotapreco.repository.EmpresaRepository;
import br.com.cotapreco.repository.UsuarioRepository;
import br.com.cotapreco.security.ServicoJwt;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

@Service @RequiredArgsConstructor
public class AutenticacaoService {
    private final UsuarioRepository repository; private final EmpresaRepository empresas; private final PasswordEncoder passwordEncoder; private final ServicoJwt jwtService;
    public RespostaLogin login(SolicitacaoLogin request) {
        Usuario user = repository.findByEmailIgnoreCase(request.email().trim()).filter(Usuario::isAtivo)
            .orElseThrow(() -> new RegraNegocioException("E-mail ou senha inválidos."));
        if (!user.getEmpresa().isAtivo() || !passwordEncoder.matches(request.password(), user.getSenhaHash()))
            throw new RegraNegocioException("E-mail ou senha inválidos.");
        return new RespostaLogin(jwtService.generate(user), "Bearer", jwtService.expirationSeconds(), view(user));
    }
    @Transactional public RespostaLogin cadastrarFarmacia(SolicitacaoCadastroFarmacia request) {
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
        return new RespostaLogin(jwtService.generate(usuario), "Bearer", jwtService.expirationSeconds(), view(usuario));
    }
    @Transactional
    public void alterarSenha(Usuario usuario, SolicitacaoAlteracaoSenha solicitacao) {
        if (!passwordEncoder.matches(solicitacao.senhaAtual(), usuario.getSenhaHash()))
            throw new RegraNegocioException("A senha atual está incorreta.");
        if (passwordEncoder.matches(solicitacao.novaSenha(), usuario.getSenhaHash()))
            throw new RegraNegocioException("A nova senha deve ser diferente da senha atual.");
        usuario.setSenhaHash(passwordEncoder.encode(solicitacao.novaSenha()));
        repository.save(usuario);
    }
    public VisaoUsuario view(Usuario user) { return new VisaoUsuario(user.getId(), user.getNome(), user.getEmail(), user.getPerfil(), user.getEmpresa().getId(), user.getEmpresa().getNome()); }
    private String gerarSlug(String nome) {
        String base = Normalizer.normalize(nome, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return (base.isBlank() ? "farmacia" : base.substring(0, Math.min(base.length(), 120))) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
