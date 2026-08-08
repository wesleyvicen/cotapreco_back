package br.com.cotapreco.exception;

import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class TratadorExcecoesApi {
    public record ErroApi(Instant timestamp, int status, String message, Map<String, String> fields) {}

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    ResponseEntity<ErroApi> notFound(RecursoNaoEncontradoException ex) { return error(HttpStatus.NOT_FOUND, ex.getMessage(), Map.of()); }

    @ExceptionHandler(RegraNegocioException.class)
    ResponseEntity<ErroApi> business(RegraNegocioException ex) { return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), Map.of()); }

    @ExceptionHandler(CredencialInvalidaException.class)
    ResponseEntity<ErroApi> invalidCredential(CredencialInvalidaException ex) { return error(HttpStatus.UNAUTHORIZED, ex.getMessage(), Map.of()); }

    @ExceptionHandler(ConflitoEstadoException.class)
    ResponseEntity<ErroApi> conflict(ConflitoEstadoException ex) { return error(HttpStatus.CONFLICT, ex.getMessage(), Map.of()); }

    @ExceptionHandler(ErroValidacaoNegocioException.class)
    ResponseEntity<ErroApi> businessValidation(ErroValidacaoNegocioException ex) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), ex.getCampos());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErroApi> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "Verifique os campos informados.", fields);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErroApi> maxUpload(MaxUploadSizeExceededException ex) { return error(HttpStatus.BAD_REQUEST, "O arquivo excede o limite de 10 MB.", Map.of()); }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErroApi> forbidden(AccessDeniedException ex) { return error(HttpStatus.FORBIDDEN, "Você não tem permissão para realizar esta operação.", Map.of()); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErroApi> generic(Exception ex) { return error(HttpStatus.INTERNAL_SERVER_ERROR, "Não foi possível concluir a operação.", Map.of()); }

    private ResponseEntity<ErroApi> error(HttpStatus status, String message, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ErroApi(Instant.now(), status.value(), message, fields));
    }
}
