package eu.btimpl.languagelearner.decks;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** A word as the app receives it. */
@Schema(name = "Word")
public record WordResponse(
    @Schema(description = "Stable id; the same word keeps it when the sheet is read again")
        String id,
    @Schema(description = "The English form shown to the student", example = "boarding pass")
        String english,
    @Schema(description = "The Hungarian form shown to the student", example = "beszállókártya")
        String hungarian,
    @Schema(description = "Every accepted English answer, the shown form included")
        List<String> acceptedEnglish,
    @Schema(description = "Every accepted Hungarian answer, the shown form included")
        List<String> acceptedHungarian,
    @Schema(description = "An example sentence, or null when there is none", nullable = true)
        String example) {

  static WordResponse of(Word word) {
    return new WordResponse(
        word.id(),
        word.english(),
        word.hungarian(),
        word.acceptedEnglish(),
        word.acceptedHungarian(),
        word.example());
  }
}
