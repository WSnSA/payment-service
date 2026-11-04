package com.wsnsa.payment.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wsnsa.payment.payment.InvalidPaymentStateException;
import com.wsnsa.payment.payment.PaymentNotFoundException;
import com.wsnsa.payment.qpay.QPayApiException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorResponse(String error, String message, Integer qpayStatus) {
    }

    @ExceptionHandler(PaymentNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(PaymentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse("PAYMENT_NOT_FOUND", e.getMessage(), null));
    }

    @ExceptionHandler(InvalidPaymentStateException.class)
    ResponseEntity<ErrorResponse> invalidState(InvalidPaymentStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse("INVALID_PAYMENT_STATE", e.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", message, null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", "Malformed request body", null));
    }

    /** QPay-ийн алдааг 502 (Bad Gateway), холбогдож чадаагүй бол 503 болгож буцаана. */
    @ExceptionHandler(QPayApiException.class)
    ResponseEntity<ErrorResponse> qpay(QPayApiException e) {
        log.warn("QPay error: {}", e.toString());
        HttpStatus status = e.getStatus() == 0 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.BAD_GATEWAY;
        return ResponseEntity.status(status)
                .body(new ErrorResponse(e.getError(), e.getMessage(), e.getStatus() == 0 ? null : e.getStatus()));
    }
}
