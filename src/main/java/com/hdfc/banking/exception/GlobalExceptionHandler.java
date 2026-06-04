package com.hdfc.banking.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.hdfc.banking.dto.ErrorResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<ErrorResponse> buildResponse(HttpStatus status,String error,String message){
        return ResponseEntity.status(status).body(
            ErrorResponse.builder()
            .status(status.value())
            .error(error)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build()
        );
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse>handleAccountNotFound(AccountNotFoundException ex){
        return buildResponse(HttpStatus.NOT_FOUND, "Account Not Found", ex.getMessage());
    }

    // ── 400 Bad Request ───────────────────────────────────────────────────────
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(InsufficientFundsException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Insufficient Funds", ex.getMessage());
    }

    @ExceptionHandler(OtpExpiredException.class)
    public ResponseEntity<ErrorResponse> handleOtpExpired(OtpExpiredException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, "OTP Expired", ex.getMessage());
    }

    @ExceptionHandler(DuplicateAccountException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateAccount(DuplicateAccountException ex) {
        return buildResponse(HttpStatus.CONFLICT, "Duplicate Account", ex.getMessage());
    }

    // ── 403 Forbidden ─────────────────────────────────────────────────────────
    @ExceptionHandler(AccountFrozenException.class)
    public ResponseEntity<ErrorResponse> handleAccountFrozen(AccountFrozenException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, "Account Frozen", ex.getMessage());
    }

    // ── 401 Unauthorized ──────────────────────────────────────────────────────
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage());
    }

    

    // ── 400 Validation Errors (@Valid on DTOs) ────────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }


    // ── 500 Catch-All (unexpected errors) ─────────────────────────────────────
    /**
     * WHY catch all Exception?
     * Safety net. If something we didn't anticipate throws, we still return
     * a proper JSON instead of an ugly HTML stack trace page.
     * The message is intentionally vague: don't expose internal details to clients.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
         ex.printStackTrace();
        return buildResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Internal Server Error",
            "An unexpected error occurred. Please try again later."
            // NOTE: log ex.getMessage() internally, but don't send it to client
        );
    }
}