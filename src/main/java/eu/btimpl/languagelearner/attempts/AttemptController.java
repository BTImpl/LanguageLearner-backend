package eu.btimpl.languagelearner.attempts;

import eu.btimpl.languagelearner.api.ApiError;
import eu.btimpl.languagelearner.auth.CurrentStudent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The answers of the signed-in student. */
@RestController
@RequestMapping("/api/v1/attempts")
class AttemptController {

  private final AttemptService service;

  AttemptController(AttemptService service) {
    this.service = service;
  }

  @PostMapping
  @Operation(
      summary = "Uploads a batch of answers in the name of the signed-in student",
      description =
          "At most 500 answers per request. Idempotent: an answer whose id the server knows is "
              + "skipped and counted as a duplicate, so the same batch can be sent again after a "
              + "network error. The deck name and the words are filled in by the server from the "
              + "student's decks.")
  @ApiResponse(responseCode = "200", description = "How many answers were saved and skipped")
  @ApiResponse(
      responseCode = "400",
      description = "Invalid request: a missing field, an unknown mode or outcome, too many items",
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
      responseCode = "503",
      description =
          "The sheet or the database cannot be used right now (SHEET_UNAVAILABLE, "
              + "DATABASE_UNAVAILABLE); nothing was lost, send the batch again later",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  AttemptsResponse upload(
      @AuthenticationPrincipal CurrentStudent student,
      @Valid @RequestBody AttemptsRequest request) {
    return service.upload(
        student,
        request.attempts().stream()
            .map(
                item ->
                    new NewAttempt(
                        item.id(),
                        item.deckId(),
                        item.wordId(),
                        item.mode(),
                        item.outcome(),
                        item.answeredAt()))
            .toList());
  }
}
