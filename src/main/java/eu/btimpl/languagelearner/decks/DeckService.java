package eu.btimpl.languagelearner.decks;

import eu.btimpl.languagelearner.sheets.DeckReader;
import eu.btimpl.languagelearner.sheets.SheetsException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;

/** The decks of a student, read from the teacher's sheet. */
@Service
public class DeckService {

  private final DeckReader reader;
  private final Clock clock;

  public DeckService(DeckReader reader, Clock clock) {
    this.reader = reader;
    this.clock = clock;
  }

  /**
   * Reads the decks on the student's tab. A student whose tab does not exist or is empty simply has
   * no decks yet.
   *
   * @throws SheetsException when the sheet cannot be read
   */
  public StudentDecks decksOf(String tab) {
    List<Deck> decks;
    try {
      decks = reader.readDecks(tab);
    } catch (SheetsException.TabNotFound e) {
      decks = List.of();
    }
    return new StudentDecks(decks, clock.instant().truncatedTo(ChronoUnit.SECONDS));
  }
}
