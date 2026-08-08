package br.com.cotapreco.exception;

public class CredencialInvalidaException extends RuntimeException {
    public CredencialInvalidaException() { super("Sessão inválida ou expirada."); }
}
