package eu.btimpl.languagelearner.decks;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** A deck with its words as the app receives it. */
@Schema(name = "Deck")
public record DeckResponse(
    @Schema(description = "Stable id of the deck") String id,
    @Schema(description = "Name of the deck", example = "Utazás") String name,
    @Schema(description = "When the backend last read this student's words from the sheet")
        Instant updatedAt,
    @Schema(description = "The words in the order of the sheet") List<WordResponse> words) {

  static DeckResponse of(Deck deck, Instant updatedAt) {
    return new DeckResponse(
        deck.id(), deck.name(), updatedAt, deck.words().stream().map(WordResponse::of).toList());
  }
}
