package br.com.cotapreco.security;

import br.com.cotapreco.repository.UsuarioRepository;
import br.com.cotapreco.repository.RepresentanteRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component @RequiredArgsConstructor
public class FiltroAutenticacaoJwt extends OncePerRequestFilter {
    private final ServicoJwt jwtService;
    private final UsuarioRepository userRepository;
    private final RepresentanteRepository representanteRepository;
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7);
            if (jwtService.isValid(token)) {
                if ("REPRESENTANTE".equals(jwtService.extrairTipo(token))) autenticarRepresentante(token);
                else autenticarUsuarioFarmacia(token);
            }
        }
        chain.doFilter(request, response);
    }

    private void autenticarUsuarioFarmacia(String token) {
        userRepository.findByEmailIgnoreCase(jwtService.extractSubject(token))
            .filter(u -> u.isAtivo() && u.getEmpresa().isAtivo()).ifPresent(user -> {
                var authority = new SimpleGrantedAuthority("ROLE_" + user.getPerfil().name());
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getEmail(), null, List.of(authority)));
            });
    }

    private void autenticarRepresentante(String token) {
        representanteRepository.findByTelefone(jwtService.extractSubject(token))
            .filter(r -> r.isAtivo() && r.getVersaoAutenticacao() == jwtService.extrairVersao(token)).ifPresent(representante -> {
                var autoridade = new SimpleGrantedAuthority("ROLE_REPRESENTANTE");
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(representante.getTelefone(), null, List.of(autoridade)));
            });
    }
}
