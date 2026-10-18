package eu.btimpl.languagelearner.attempts;

import java.time.Instant;
import java.util.UUID;

/** An answer as the student's app reports it, before the word is looked up. */
public record NewAttempt(
    UUID id, String deckId, String wordId, Mode mode, Outcome outcome, Instant answeredAt) {}
