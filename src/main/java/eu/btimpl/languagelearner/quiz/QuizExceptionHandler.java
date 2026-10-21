package eu.btimpl.languagelearner.quiz;

import eu.btimpl.languagelearner.api.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

/**
 * The errors of the photo quiz endpoint, in the uniform {@link ApiError} body. It is not limited to
 * the controller, because an upload that is too large is refused while the request is still being
 * read, before any controller is chosen. It runs before the general handler of the app.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class QuizExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(QuizExceptionHandler.class);

  @ExceptionHandler(QuizImageException.class)
  ResponseEntity<ApiError> imageRejected(QuizImageException e) {
    return ResponseEntity.status(e.status()).body(new ApiError(e.code(), e.getMessage()));
  }

  /** The upload went over the limit of the server's multipart settings. */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException e) {
    QuizImageException tooLarge = QuizImageException.tooLarge();
    return ResponseEntity.status(tooLarge.status())
        .body(new ApiError(tooLarge.code(), tooLarge.getMessage()));
  }

  /** The multipart body could not be read at all. */
  @ExceptionHandler(MultipartException.class)
  ResponseEntity<ApiError> unreadableUpload(MultipartException e) {
    QuizImageException missing = QuizImageException.missing();
    return ResponseEntity.status(missing.status())
        .body(new ApiError(missing.code(), missing.getMessage()));
  }

  /** The student has used all the quizzes of the day. */
  @ExceptionHandler(DailyLimitReachedException.class)
  ResponseEntity<DailyLimitError> dailyLimitReached(DailyLimitReachedException e) {
    log.info("A student reached the daily limit of {} quizzes", e.limit());
    long seconds = Math.max(1, e.retryAfter().toSeconds());
    return ResponseEntity.status(429)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(seconds))
        .body(DailyLimitError.of(e));
  }

  @ExceptionHandler(NoQuestionsFoundException.class)
  ResponseEntity<ApiError> noQuestions(NoQuestionsFoundException e) {
    return ResponseEntity.status(422)
        .body(new ApiError("NO_QUESTIONS_FOUND", "No question could be read from the image"));
  }

  @ExceptionHandler(ExtractionUnavailableException.class)
  ResponseEntity<ApiError> extractionUnavailable(ExtractionUnavailableException e) {
    // Only the message is logged (it names the status, never the image or the key).
    log.warn(
        "The image could not be read: {}{}",
        e.getMessage(),
        e.getCause() == null ? "" : " (" + e.getCause().getClass().getSimpleName() + ")");
    return ResponseEntity.status(503)
        .body(
            new ApiError(
                "EXTRACTION_UNAVAILABLE", "The image cannot be read right now, try again later"));
  }
}
