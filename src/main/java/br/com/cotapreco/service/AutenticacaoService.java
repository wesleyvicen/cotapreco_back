package br.com.cotapreco.service;

import br.com.cotapreco.dto.AutenticacaoDtos.*;
import br.com.cotapreco.exception.RegraNegocioException;
import br.com.cotapreco.model.Usuario;
import br.com.cotapreco.repository.UsuarioRepository;
import br.com.cotapreco.security.ServicoJwt;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service @RequiredArgsConstructor
public class AutenticacaoService {
    private final UsuarioRepository repository; private final PasswordEncoder passwordEncoder; private final ServicoJwt jwtService;
    public RespostaLogin login(SolicitacaoLogin request) {
        Usuario user = repository.findByEmailIgnoreCase(request.email().trim()).filter(Usuario::isAtivo)
            .orElseThrow(() -> new RegraNegocioException("E-mail ou senha inválidos."));
        if (!user.getEmpresa().isAtivo() || !passwordEncoder.matches(request.password(), user.getSenhaHash()))
            throw new RegraNegocioException("E-mail ou senha inválidos.");
        return new RespostaLogin(jwtService.generate(user), "Bearer", jwtService.expirationSeconds(), view(user));
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
}
