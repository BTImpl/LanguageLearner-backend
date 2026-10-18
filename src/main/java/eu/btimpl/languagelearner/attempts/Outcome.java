package eu.btimpl.languagelearner.attempts;

/** How a word went. Stored by name; the names are fixed in the schema. */
public enum Outcome {
  /** Right at the first answer. */
  FIRST_TRY,
  /** Right, but only after the student asked for help. */
  WITH_HELP,
  /** The first answer was wrong. */
  WRONG_FIRST
}
