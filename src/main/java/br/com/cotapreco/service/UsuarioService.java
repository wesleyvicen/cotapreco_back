package br.com.cotapreco.service;

import br.com.cotapreco.dto.AutenticacaoDtos.SolicitacaoNovoUsuario;
import br.com.cotapreco.dto.AutenticacaoDtos.VisaoUsuarioAdministracao;
import br.com.cotapreco.exception.RegraNegocioException;
import br.com.cotapreco.model.Usuario;
import br.com.cotapreco.repository.UsuarioRepository;
import br.com.cotapreco.security.UsuarioAtualService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service @RequiredArgsConstructor
public class UsuarioService {
    private final UsuarioRepository usuarios;
    private final UsuarioAtualService usuarioAtual;
    private final PasswordEncoder codificador;

    @Transactional(readOnly = true)
    public List<VisaoUsuarioAdministracao> listar() {
        return usuarios.findAllByEmpresaIdOrderByNomeAsc(usuarioAtual.companyId()).stream().map(this::visualizar).toList();
    }

    @Transactional
    public VisaoUsuarioAdministracao criar(SolicitacaoNovoUsuario solicitacao) {
        String email = solicitacao.email().trim().toLowerCase(Locale.ROOT);
        if (usuarios.existsByEmailIgnoreCase(email)) throw new RegraNegocioException("Este e-mail já está cadastrado.");
        Usuario usuario = new Usuario();
        usuario.setEmpresa(usuarioAtual.get().getEmpresa());
        usuario.setNome(solicitacao.nome().trim());
        usuario.setEmail(email);
        usuario.setSenhaHash(codificador.encode(solicitacao.senha()));
        usuario.setPerfil(solicitacao.perfil());
        return visualizar(usuarios.save(usuario));
    }

    private VisaoUsuarioAdministracao visualizar(Usuario usuario) {
        return new VisaoUsuarioAdministracao(usuario.getId(), usuario.getNome(), usuario.getEmail(), usuario.getPerfil(), usuario.isAtivo(), usuario.getCriadoEm());
    }
}
