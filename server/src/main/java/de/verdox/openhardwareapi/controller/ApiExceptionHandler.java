package de.verdox.openhardwareapi.controller;

import de.verdox.openhardwareapi.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.jboss.logging.MDC;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.ConversionFailedException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final URI DEFAULT_TYPE = URI.create("about:blank");

    private ProblemDetail pd(HttpStatus status, String title, String detail, HttpServletRequest req) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title != null ? title : status.getReasonPhrase());
        pd.setType(DEFAULT_TYPE);
        pd.setProperty("timestamp", OffsetDateTime.now());
        pd.setProperty("instance", req.getRequestURI());
        pd.setProperty("requestId", Optional.ofNullable(MDC.get("requestId")).orElse(null));
        return pd;
    }

    // 404 – Domain Not Found
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ProblemDetail> notFound(NoSuchElementException ex, HttpServletRequest req) {
        ProblemDetail p = pd(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage(), req);
        p.setProperty("code", "not_found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(p);
    }

    // 400 – Parameter/Typ/Validation (simple)
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, ConversionFailedException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> badRequest(Exception ex, HttpServletRequest req) {
        ProblemDetail p = pd(HttpStatus.BAD_REQUEST, "Bad request", ex.getMessage(), req);
        p.setProperty("code", "bad_request");
        return ResponseEntity.badRequest().body(p);
    }

    // 400 – JSON/Body unlesbar
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        ProblemDetail p = pd(HttpStatus.BAD_REQUEST, "Malformed JSON", rootMsg(ex), req);
        p.setProperty("code", "malformed_json");
        return ResponseEntity.badRequest().body(p);
    }

    // 400 – Bean Validation (query/path)
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> constraint(ConstraintViolationException ex, HttpServletRequest req) {
        ProblemDetail p = pd(HttpStatus.BAD_REQUEST, "Constraint violation", "One or more constraints failed", req);
        p.setProperty("code", "constraint_violation");
        p.setProperty("violations", ex.getConstraintViolations().stream().map(v -> Map.of(
                "property", v.getPropertyPath().toString(),
                "message", v.getMessage(),
                "invalid", String.valueOf(v.getInvalidValue())
        )).toList());
        return ResponseEntity.badRequest().body(p);
    }

    // 422 – Bean Validation (DTO @Valid)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> notValid(MethodArgumentNotValidException ex, HttpServletRequest req) {
        ProblemDetail p = pd(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed", "Request body is invalid", req);
        p.setProperty("code", "validation_failed");
        p.setProperty("violations", ex.getBindingResult().getFieldErrors().stream().map(fe -> Map.of(
                "field", fe.getField(),
                "message", fe.getDefaultMessage(),
                "rejected", String.valueOf(fe.getRejectedValue())
        )).toList());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(p);
    }

    // 405 – Methode nicht erlaubt
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> methodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        ProblemDetail p = pd(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", ex.getMessage(), req);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(ex.getSupportedHttpMethods() != null ? ex.getSupportedHttpMethods().toArray(HttpMethod[]::new) : new HttpMethod[0])
                .body(p);
    }

    // 415 – Media Type
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ProblemDetail> unsupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        ProblemDetail p = pd(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", ex.getMessage(), req);
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(p);
    }

    // 409 – DB Konflikte
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> conflict(Exception ex, HttpServletRequest req) {
        ProblemDetail p = pd(HttpStatus.CONFLICT, "Conflict", "Data integrity violation", req);
        p.setProperty("code", "data_integrity_violation");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(p);
    }

    // 400 – eure eigene BadRequestException (mit ProblemDetail drin)
    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ProblemDetail> onBadRequest(BadRequestException ex, HttpServletRequest req) {
        ProblemDetail problem = ex.getProblem();
        enrich(problem, req, "bad_request_custom");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    // Upstream (WebClient) – Status & Body durchreichen
    @ExceptionHandler(org.springframework.web.reactive.function.client.WebClientResponseException.class)
    public ResponseEntity<ProblemDetail> upstream(org.springframework.web.reactive.function.client.WebClientResponseException ex, HttpServletRequest req) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        ProblemDetail p = pd(status, "Upstream error", "Remote service responded with error", req);
        p.setProperty("code", "upstream_error");
        p.setProperty("upstreamStatus", ex.getStatusCode().value());
        p.setProperty("upstreamBody", ex.getResponseBodyAsString());
        return ResponseEntity.status(status).body(p);
    }

    // Letzte Rettung – 500
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> generic(Exception ex, HttpServletRequest req) {
        ProblemDetail p = pd(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "Unexpected error", req);
        p.setProperty("code", "unexpected_error");
        // Wichtig: sauber loggen (mit Request-ID)
        LoggerFactory.getLogger(getClass()).error("Unhandled exception (requestId={}): {}",
                MDC.get("requestId"), ex.toString(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(p);
    }

    private void enrich(ProblemDetail pd, HttpServletRequest req, String code) {
        if (pd.getTitle() == null) pd.setTitle(HttpStatus.valueOf(pd.getStatus()).getReasonPhrase());
        if (pd.getType() == null) pd.setType(DEFAULT_TYPE);
        pd.setProperty("timestamp", OffsetDateTime.now());
        pd.setProperty("instance", req.getRequestURI());
        pd.setProperty("requestId", Optional.ofNullable(MDC.get("requestId")).orElse(null));
        if (!pd.getProperties().containsKey("code")) pd.setProperty("code", code);
    }

    private String rootMsg(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null) r = r.getCause();
        return r.getMessage() != null ? r.getMessage() : t.toString();
    }
}
