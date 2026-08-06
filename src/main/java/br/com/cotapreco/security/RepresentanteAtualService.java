package br.com.cotapreco.security;

import br.com.cotapreco.exception.RecursoNaoEncontradoException;
import br.com.cotapreco.model.Representante;
import br.com.cotapreco.repository.RepresentanteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class RepresentanteAtualService {
    private final RepresentanteRepository repositorio;

    public Representante obter() {
        String telefone = SecurityContextHolder.getContext().getAuthentication().getName();
        return repositorio.findByTelefone(telefone)
            .filter(Representante::isAtivo)
            .orElseThrow(() -> new RecursoNaoEncontradoException("Representante não encontrado."));
    }
}
