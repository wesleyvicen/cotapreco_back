package br.com.cotapreco.exception;

import java.util.Map;

public class ErroValidacaoNegocioException extends RuntimeException {
    private final Map<String, String> campos;

    public ErroValidacaoNegocioException(String mensagem, Map<String, String> campos) {
        super(mensagem);
        this.campos = Map.copyOf(campos);
    }

    public Map<String, String> getCampos() { return campos; }
}
