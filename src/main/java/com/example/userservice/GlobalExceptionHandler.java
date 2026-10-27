package com.example.userservice;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns exceptions into consistent JSON error responses (RFC 7807 "problem details"),
 * e.g. {"status":404,"title":"User not found","detail":"User not found with id: 5"}.
 *
 * Extending ResponseEntityExceptionHandler means Spring's built-in errors
 * (malformed JSON, wrong HTTP method, etc.) use the same format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ProblemDetail handleNotFound(UserNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "User not found", ex.getMessage());
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ProblemDetail handleDuplicateEmail(DuplicateEmailException ex) {
        return problem(HttpStatus.CONFLICT, "Email already in use", ex.getMessage());
    }

    // Safety net: two requests racing to create the same email both pass the
    // existsByEmail check, then the database's unique constraint rejects one.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Conflict", "The request conflicts with existing data (is that email already in use?)");
    }

    @ExceptionHandler(DemoLimitReachedException.class)
    public ProblemDetail handleDemoLimit(DemoLimitReachedException ex) {
        return problem(HttpStatus.TOO_MANY_REQUESTS, "Demo limit reached", ex.getMessage());
    }

    /** Validation failures (blank name, bad email...) list each field's problem. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
