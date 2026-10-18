package eu.btimpl.languagelearner.api;

import eu.btimpl.languagelearner.sheets.SheetsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error of the controllers into the uniform {@link ApiError} body: the errors Spring
 * MVC raises itself (unknown path, wrong method, bad request body, ...) and the ones of the app.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  /** The spreadsheet cannot be read right now, or is not set up properly. */
  @ExceptionHandler(SheetsException.class)
  ResponseEntity<ApiError> sheetUnavailable(SheetsException e) {
    log.error("The spreadsheet could not be read", e);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new ApiError("SHEET_UNAVAILABLE", "The word list is not available right now"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e) {
    log.error("Unexpected error", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ApiError("INTERNAL_ERROR", "Something went wrong"));
  }

  /** The errors Spring MVC raises itself all end up here. */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex,
      Object body,
      HttpHeaders headers,
      HttpStatusCode statusCode,
      WebRequest request) {
    HttpStatus status = HttpStatus.resolve(statusCode.value());
    String code = status == null ? "ERROR" : status.name();
    String message = status == null ? "The request failed" : status.getReasonPhrase();
    return ResponseEntity.status(statusCode).headers(headers).body(new ApiError(code, message));
  }
}
