package eu.btimpl.languagelearner.attempts;

import eu.btimpl.languagelearner.auth.CurrentStudent;
import eu.btimpl.languagelearner.decks.Deck;
import eu.btimpl.languagelearner.decks.DeckService;
import eu.btimpl.languagelearner.decks.Word;
import eu.btimpl.languagelearner.sheets.SheetsException;
import eu.btimpl.languagelearner.sheets.Student;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Stores the answers a student uploads. */
@Service
public class AttemptService {

  private final DeckService decks;
  private final StudentRegistry registry;
  private final AttemptRepository attempts;
  private final Clock clock;

  public AttemptService(
      DeckService decks, StudentRegistry registry, AttemptRepository attempts, Clock clock) {
    this.decks = decks;
    this.registry = registry;
    this.attempts = attempts;
    this.clock = clock;
  }

  /**
   * Stores the answers in the name of the student. An answer whose id is known already is skipped,
   * so the same batch can be sent again after a network error. The deck name and the words are
   * copied from the student's current decks; an answer about a deck or word that is not there any
   * more (or never was) is stored with empty copies.
   *
   * @throws SheetsException when the decks cannot be read; the app should send the batch again
   * @throws org.springframework.dao.DataAccessException when the database cannot be used
   */
  public AttemptsResponse upload(CurrentStudent student, List<NewAttempt> items) {
    if (items.isEmpty()) {
      return new AttemptsResponse(0, 0);
    }
    long studentId = registry.register(new Student(student.email(), student.name(), student.tab()));
    Map<String, Deck> decksById = new HashMap<>();
    for (Deck deck : decks.decksOf(student.tab()).decks()) {
      decksById.putIfAbsent(deck.id(), deck);
    }
    Instant receivedAt = clock.instant();
    List<Attempt> toSave =
        items.stream().map(item -> attemptOf(studentId, item, decksById, receivedAt)).toList();

    int saved = attempts.saveAll(toSave);
    return new AttemptsResponse(saved, toSave.size() - saved);
  }

  private static Attempt attemptOf(
      long studentId, NewAttempt item, Map<String, Deck> decksById, Instant receivedAt) {
    Optional<Deck> deck = Optional.ofNullable(decksById.get(item.deckId()));
    Optional<Word> word =
        deck.flatMap(d -> d.words().stream().filter(w -> w.id().equals(item.wordId())).findFirst());
    return new Attempt(
        item.id(),
        studentId,
        item.deckId(),
        deck.map(Deck::name).orElse(""),
        item.wordId(),
        word.map(Word::english).orElse(""),
        word.map(Word::hungarian).orElse(""),
        item.mode(),
        item.outcome(),
        item.answeredAt(),
        receivedAt);
  }
}
