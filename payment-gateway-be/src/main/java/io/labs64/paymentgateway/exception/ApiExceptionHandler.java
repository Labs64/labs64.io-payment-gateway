package io.labs64.paymentgateway.exception;

import io.labs64.paymentgateway.config.CorrelationIdFilter;
import io.labs64.paymentgateway.dto.ApiError;
import java.time.Instant;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ApiError> handleNotFound(NotFoundException ex) {
		return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	@ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
	public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
		String message = ex instanceof MethodArgumentNotValidException manve
				? "Validation failed"
				: ex.getMessage();
		return buildResponse(HttpStatus.BAD_REQUEST, message);
	}

	@ExceptionHandler(IllegalStateException.class)
	public ResponseEntity<ApiError> handleConflict(IllegalStateException ex) {
		return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiError> handleGeneral(Exception ex) {
		return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
	}

	private ResponseEntity<ApiError> buildResponse(HttpStatus status, String message) {
		String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
		ApiError error = new ApiError(
				Instant.now(),
				status.value(),
				status.getReasonPhrase(),
				message,
				correlationId);
		return ResponseEntity.status(status).body(error);
	}
}
