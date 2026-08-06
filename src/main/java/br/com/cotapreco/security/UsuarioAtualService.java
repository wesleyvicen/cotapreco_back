package br.com.cotapreco.security;

import br.com.cotapreco.exception.RecursoNaoEncontradoException;
import br.com.cotapreco.model.Usuario;
import br.com.cotapreco.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class UsuarioAtualService {
    private final UsuarioRepository repository;
    public Usuario get() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return repository.findByEmailIgnoreCase(email).orElseThrow(() -> new RecursoNaoEncontradoException("Usuário não encontrado."));
    }
    public Long companyId() { return get().getEmpresa().getId(); }
}
