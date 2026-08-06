package br.com.cotapreco.dto;
import jakarta.validation.constraints.*;
public final class EmpresaDtos {
    private EmpresaDtos(){}
    public record VisaoEmpresa(Long id,String nome,String cnpj){}
    public record SolicitacaoEmpresa(@NotBlank @Size(max=160) String nome,
        @NotBlank @Pattern(regexp="\\d{14}",message="CNPJ deve conter 14 dígitos") String cnpj){}
}
