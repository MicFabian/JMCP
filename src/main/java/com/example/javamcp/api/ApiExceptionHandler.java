package com.example.javamcp.api;

import com.github.javaparser.ParseProblemException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onValidation(MethodArgumentNotValidException exception,
                                      HttpServletRequest request) {
        ProblemDetail problemDetail = problemDetail(HttpStatus.BAD_REQUEST, "Validation failed", "Invalid request payload", request);
        List<Map<String, Object>> errors = exception.getBindingResult().getAllErrors().stream()
                .map(error -> {
                    Map<String, Object> details = new java.util.LinkedHashMap<>();
                    details.put("field", error instanceof org.springframework.validation.FieldError fieldError
                            ? fieldError.getField()
                            : error.getObjectName());
                    details.put("message", error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage());
                    if (error instanceof org.springframework.validation.FieldError fieldError && fieldError.getRejectedValue() != null) {
                        details.put("rejectedValue", String.valueOf(fieldError.getRejectedValue()));
                    }
                    return details;
                })
                .toList();
        if (!errors.isEmpty()) {
            problemDetail.setProperty("errors", errors);
        }
        return problemDetail;
    }

    @ExceptionHandler(ParseProblemException.class)
    public ProblemDetail onParse(ParseProblemException exception,
                                 HttpServletRequest request) {
        return problemDetail(HttpStatus.BAD_REQUEST, "Java parse error", "Could not parse Java input", request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onIllegalArgument(IllegalArgumentException exception,
                                           HttpServletRequest request) {
        return problemDetail(
                HttpStatus.BAD_REQUEST,
                "Bad request",
                exception.getMessage() == null ? "Invalid request argument" : exception.getMessage(),
                request
        );
    }

    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail onIllegalState(IllegalStateException exception,
                                        HttpServletRequest request) {
        return problemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal error",
                exception.getMessage() == null ? "Internal server error" : exception.getMessage(),
                request
        );
    }

    private ProblemDetail problemDetail(HttpStatus status,
                                        String title,
                                        String detail,
                                        HttpServletRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        if (request != null) {
            problemDetail.setInstance(URI.create(request.getRequestURI()));
            problemDetail.setProperty("path", request.getRequestURI());
        }
        problemDetail.setProperty("timestamp", Instant.now(clock).toString());
        return problemDetail;
    }
}
