package com.telusko.airline.controller;

import com.telusko.airline.dto.ApiDtos.ApiError;
import dev.langchain4j.guardrail.InputGuardrailException;
import dev.langchain4j.guardrail.OutputGuardrailException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.format.DateTimeParseException;

/**
 * Turns exceptions into JSON the frontend can act on.
 * <p>
 * Without this, a bad PNR reaches the browser as a Spring stack trace page, and the React
 * app has nothing to show the passenger. Each handler maps to the status the frontend
 * actually branches on.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Turns a framework exception into a sentence somebody can act on. */
    private static String readable(Exception ex) {
        if (ex instanceof MissingServletRequestParameterException missing) {
            return "The " + missing.getParameterName() + " is missing.";
        }
        if (ex instanceof DateTimeParseException) {
            return "That date is not valid. Use the format yyyy-MM-dd.";
        }
        if (ex instanceof MethodArgumentTypeMismatchException mismatch) {
            return "The value for " + mismatch.getName() + " is not valid.";
        }
        return ex.getMessage() == null ? "The request is not valid." : ex.getMessage();
    }

    /** Unknown flight, unknown PNR, a booking that is not yours. All 404. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> notFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("not_found", ex.getMessage()));
    }

    /** A full flight, a cancelled flight, a booking already refunded. The request was
        understood and the state does not allow it, which is 409 and not 400. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> conflict(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiError("conflict", ex.getMessage()));
    }

    /**
     * Maps invalid dates, missing parameters, and conversion failures to readable 400
     * responses. SecurityConfig permits the error redispatch used by Spring.
     */
    @ExceptionHandler({
            DateTimeParseException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> badRequest(Exception ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError("invalid_request", readable(ex)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> invalid(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .orElse("Request is not valid.");

        return ResponseEntity.badRequest().body(new ApiError("invalid_request", detail));
    }

    /**
     * A guardrail refused the input.
     * <p>
     * 400 rather than 500, and the guardrail's own message is passed through because it was
     * written for the passenger to read. Most callers never see this: the assistant service
     * catches it and returns the message as a normal reply, which keeps a blocked prompt
     * looking like a conversation rather than an error.
     */
    @ExceptionHandler(InputGuardrailException.class)
    public ResponseEntity<ApiError> inputRejected(InputGuardrailException ex) {
        return ResponseEntity.badRequest().body(new ApiError("input_rejected", ex.getMessage()));
    }

    /**
     * The model could not produce an acceptable answer, even after the retries.
     * <p>
     * A 502 identifies the model output as the failed upstream dependency while preserving
     * the successful guardrail decision.
     */
    @ExceptionHandler(OutputGuardrailException.class)
    public ResponseEntity<ApiError> outputRejected(OutputGuardrailException ex) {
        log.warn("Output guardrail exhausted its retries: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(new ApiError("answer_rejected",
                        "I could not produce a reliable answer to that. Please try rephrasing."));
    }

    /**
     * Anything not named above.
     * <p>
     * The message is deliberately not passed through. An unexpected exception message can
     * carry a SQL fragment or a file path, and neither of those belongs in a passenger's
     * browser. The log gets the full thing.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(new ApiError("server_error",
                        "Something went wrong on our side. Please try again."));
    }
}
