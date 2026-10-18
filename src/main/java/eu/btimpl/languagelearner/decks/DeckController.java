package eu.btimpl.languagelearner.decks;

import eu.btimpl.languagelearner.api.ApiError;
import eu.btimpl.languagelearner.auth.CurrentStudent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The decks of the signed-in student. */
@RestController
@RequestMapping("/api/v1/decks")
class DeckController {

  private final DeckService service;

  DeckController(DeckService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "The signed-in student's decks with all their words",
      description =
          "Decks come sorted by name, the words in the order of the sheet. A student whose tab "
              + "is missing or empty gets an empty list. The answer may come from a cache; "
              + "updatedAt tells when the sheet was read.")
  @ApiResponse(responseCode = "200", description = "The decks, possibly none")
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
      description = "The sheet cannot be read right now (SHEET_UNAVAILABLE)",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  List<DeckResponse> decks(@AuthenticationPrincipal CurrentStudent student) {
    return respond(service.decksOf(student.tab()));
  }

  @PostMapping("/refresh")
  @Operation(
      summary = "Reads the sheet again and returns the signed-in student's decks",
      description =
          "Same answer as the GET. A student can refresh once per 10 seconds; asking more often "
              + "returns the cached decks. If the sheet cannot be read but decks were cached, "
              + "those come back with their old updatedAt.")
  @ApiResponse(responseCode = "200", description = "The decks, possibly none")
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
      description = "The sheet cannot be read and nothing is cached (SHEET_UNAVAILABLE)",
      content = @Content(schema = @Schema(implementation = ApiError.class)))
  List<DeckResponse> refresh(@AuthenticationPrincipal CurrentStudent student) {
    return respond(service.refreshOf(student.email(), student.tab()));
  }

  private static List<DeckResponse> respond(StudentDecks result) {
    return result.decks().stream().map(deck -> DeckResponse.of(deck, result.updatedAt())).toList();
  }
}
