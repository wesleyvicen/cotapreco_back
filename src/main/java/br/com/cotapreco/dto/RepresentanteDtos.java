package br.com.cotapreco.dto;

import jakarta.validation.constraints.*;

public final class RepresentanteDtos {
    private RepresentanteDtos() {}

    public record SolicitacaoCadastroRepresentante(
        @NotBlank String tokenCotacao,
        @NotBlank @Size(max = 120) String nome,
        @NotBlank @Size(max = 30) String telefone,
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(max = 72) String senha) {}

    public record SolicitacaoLoginRepresentante(
        @NotBlank @Size(max = 30) String telefone,
        @NotBlank String senha) {}

    public record VisaoRepresentante(Long id, String nome, String telefone, String email) {}
    public record RespostaAutenticacaoRepresentante(String token, String tipoToken, long expiraEmSegundos, VisaoRepresentante representante) {}
    public record SolicitacaoEsqueciSenha(@NotBlank @Email @Size(max = 180) String email) {}
    public record SolicitacaoRedefinicaoSenha(@NotBlank String token, @NotBlank @Size(max = 72) String novaSenha) {}
    public record MensagemRepresentante(String mensagem) {}
}
