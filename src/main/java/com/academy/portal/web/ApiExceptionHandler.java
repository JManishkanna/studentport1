package com.academy.portal.web;

import com.academy.portal.service.BadRequestException;
import com.academy.portal.service.ConflictException;
import com.academy.portal.service.NotFoundException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler {

    /** Covers MethodArgumentNotValidException and its Spring 6.2 handler-validation subclasses. */
    @ExceptionHandler(BindException.class)
    public ProblemDetail onInvalidBody(BindException ex) {
        List<String> messages = ex.getBindingResult().getFieldErrors().stream()
                .map(this::describe)
                .distinct()
                .sorted()
                .toList();
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                messages.isEmpty() ? "Request payload did not pass validation" : String.join("; ", messages));
        detail.setTitle("Invalid request");
        return detail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail onConstraintViolation(DataIntegrityViolationException ex) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "That change breaks a database rule, most likely a duplicate roll number, email or class slot");
        detail.setTitle("Data conflict");
        return detail;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail onIllegalArgument(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // The service layer words these for a person to read, so they become the response detail.
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail onNotFound(NotFoundException ex) {
        return describe(HttpStatus.NOT_FOUND, "Not found", ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail onConflict(ConflictException ex) {
        return describe(HttpStatus.CONFLICT, "Already exists", ex.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail onBadRequest(BadRequestException ex) {
        return describe(HttpStatus.BAD_REQUEST, "Invalid request", ex.getMessage());
    }

    private ProblemDetail describe(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }

    private String describe(FieldError error) {
        String message = error.getDefaultMessage() == null ? "is required" : error.getDefaultMessage();
        String label = label(error.getField());
        // Some constraints already name the field in their message; do not print it twice.
        if (message.regionMatches(true, 0, label, 0, label.length())) {
            return message;
        }
        return label + " " + Character.toLowerCase(message.charAt(0)) + message.substring(1);
    }

    /** "rollNumber" becomes "Roll number" so the message reads like a sentence. */
    private String label(String field) {
        String spaced = field.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1).toLowerCase();
    }
}
