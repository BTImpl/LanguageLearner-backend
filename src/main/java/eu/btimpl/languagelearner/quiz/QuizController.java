package eu.btimpl.languagelearner.quiz;

import eu.btimpl.languagelearner.api.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Turns the photo of a printed worksheet into a quiz. The photo is only held in memory while the
 * request runs; it is never stored, and neither it nor the questions are logged.
 */
@RestController
@RequestMapping("/api/v1/quizzes")
class QuizController {

  private static final Logger log = LoggerFactory.getLogger(QuizController.class);

  /**
   * The largest accepted image. {@code spring.servlet.multipart.max-file-size} in {@code
   * application.yml} has the same value, so a larger file is also stopped while it is received.
   */
  static final long MAX_IMAGE_BYTES = 8L * 1024 * 1024;

  private final QuizExtractor extractor;

  QuizController(QuizExtractor extractor) {
    this.extractor = extractor;
  }

  @PostMapping("/from-image")
  @Operation(
      summary = "Reads the questions of a photographed worksheet",
      description =
          "Send a JPEG or PNG photo of at most 8 MB in the 'image' field of a "
              + "multipart/form-data request. The multiple-choice questions and their options are "
              + "copied from the sheet; nothing is answered, so no option is marked as correct. "
              + "The photo is not stored. A call can take 10-20 seconds.")
  @ApiResponse(
      responseCode = "200",
      description = "The quiz; skippedQuestions counts the incomplete questions left out")
  @ApiResponse(
      responseCode = "400",
      description = "There is no image in the 'image' field (IMAGE_MISSING)",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "401",
      description = "No valid sign-in token (UNAUTHORIZED)",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "403",
      description = "The account is not on the student list (STUDENT_NOT_FOUND)",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "413",
      description = "The image is larger than 8 MB (IMAGE_TOO_LARGE)",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "415",
      description = "The image is neither a JPEG nor a PNG (IMAGE_TYPE_UNSUPPORTED)",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "422",
      description = "No question could be read completely from the image (NO_QUESTIONS_FOUND)",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  @ApiResponse(
      responseCode = "503",
      description =
          "The service that reads the image is not available or took too long "
              + "(EXTRACTION_UNAVAILABLE); trying again later may work",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  QuizResponse fromImage(@RequestPart(name = "image", required = false) MultipartFile image) {
    if (image == null || image.isEmpty()) {
      throw QuizImageException.missing();
    }
    if (image.getSize() > MAX_IMAGE_BYTES) {
      throw QuizImageException.tooLarge();
    }
    byte[] bytes = read(image);
    QuizImageType type =
        QuizImageType.detect(bytes).orElseThrow(QuizImageException::unsupportedType);

    long started = System.nanoTime();
    try {
      QuizResponse response = QuizResponse.of(extractor.extract(bytes, type.mediaType()));
      log.info(
          "Quiz made from an image of {} KB: {} questions, {} skipped, {} ms",
          bytes.length / 1024,
          response.questions().size(),
          response.skippedQuestions(),
          millisSince(started));
      return response;
    } catch (NoQuestionsFoundException | ExtractionUnavailableException e) {
      log.info(
          "No quiz made from an image of {} KB ({}) after {} ms",
          bytes.length / 1024,
          e.getClass().getSimpleName(),
          millisSince(started));
      throw e;
    }
  }

  private static byte[] read(MultipartFile image) {
    try {
      return image.getBytes();
    } catch (IOException e) {
      throw QuizImageException.missing();
    }
  }

  private static long millisSince(long startedNanos) {
    return (System.nanoTime() - startedNanos) / 1_000_000;
  }
}
