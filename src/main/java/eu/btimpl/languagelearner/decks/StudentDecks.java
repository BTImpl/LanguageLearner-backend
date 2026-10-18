package eu.btimpl.languagelearner.decks;

import java.time.Instant;
import java.util.List;

/**
 * The decks of one student as read from the sheet.
 *
 * @param decks the decks, sorted by name
 * @param updatedAt when the backend read them from the sheet, to the second
 */
public record StudentDecks(List<Deck> decks, Instant updatedAt) {

  public StudentDecks {
    decks = List.copyOf(decks);
  }
}
