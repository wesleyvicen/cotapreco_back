package br.com.cotapreco.dto;

import br.com.cotapreco.enums.PerfilUsuario;
import jakarta.validation.constraints.*;
import java.time.Instant;

public final class AutenticacaoDtos {
    private AutenticacaoDtos() {}
    public record SolicitacaoLogin(@NotBlank @Email String email, @NotBlank String password) {}
    public record SolicitacaoCadastroFarmacia(
        @NotBlank @Size(max = 120) String nomeUsuario,
        @NotBlank @Size(max = 160) String nomeFarmacia,
        @NotBlank @Pattern(regexp = "\\d{14}", message = "Informe os 14 dígitos do CNPJ.") String cnpj,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 72) String senha) {}
    public record SolicitacaoNovoUsuario(
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 8, max = 72) String senha,
        @NotNull PerfilUsuario perfil) {}
    public record SolicitacaoAlteracaoSenha(
        @NotBlank String senhaAtual,
        @NotBlank @Size(min = 8, max = 72) String novaSenha) {}
    public record VisaoUsuario(Long id, String name, String email, PerfilUsuario role, Long companyId, String companyName) {}
    public record VisaoUsuarioAdministracao(Long id, String name, String email, PerfilUsuario role, boolean active, Instant createdAt) {}
    public record RespostaLogin(String token, String tokenType, long expiresIn, VisaoUsuario user) {}
}
