package br.com.cotapreco.dto;

import br.com.cotapreco.enums.PerfilUsuario;
import jakarta.validation.constraints.*;

public final class AutenticacaoDtos {
    private AutenticacaoDtos() {}
    public record SolicitacaoLogin(@NotBlank @Email String email, @NotBlank String password) {}
    public record VisaoUsuario(Long id, String name, String email, PerfilUsuario role, Long companyId, String companyName) {}
    public record RespostaLogin(String token, String tokenType, long expiresIn, VisaoUsuario user) {}
}
