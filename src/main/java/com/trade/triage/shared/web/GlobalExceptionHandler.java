package com.trade.triage.shared.web;

import com.trade.triage.shared.exception.BusinessException;
import com.trade.triage.shared.exception.InvalidPayloadException;
import com.trade.triage.shared.exception.NotFoundException;
import com.trade.triage.shared.exception.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> onValidation(MethodArgumentNotValidException exception) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("VALIDATION_FAILED", "Payload invalido", details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> onUnreadableBody(HttpMessageNotReadableException exception) {
        LOG.warn("corpo de requisicao ilegivel: {}", exception.getMessage());
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("MALFORMED_BODY", "Corpo da requisicao ilegivel"));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> onBusiness(BusinessException exception) {
        LOG.warn("business failure code={} message={}", exception.code(), exception.getMessage());
        return ResponseEntity.status(statusOf(exception))
                .body(ErrorResponse.of(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> onUnexpected(Exception exception) {
        LOG.error("unexpected failure", exception);
        return ResponseEntity.internalServerError()
                .body(ErrorResponse.of("INTERNAL_ERROR", "Falha interna"));
    }

    private HttpStatus statusOf(BusinessException exception) {
        return switch (exception) {
            case NotFoundException ignored -> HttpStatus.NOT_FOUND;
            case UnauthorizedException ignored -> HttpStatus.UNAUTHORIZED;
            case InvalidPayloadException ignored -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
    }
}
