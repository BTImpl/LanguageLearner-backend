package eu.btimpl.languagelearner.quiz;

/** The photo did not contain a single question that could be read completely. */
public class NoQuestionsFoundException extends RuntimeException {

  public NoQuestionsFoundException() {
    super("No usable multiple-choice question was found on the image");
  }
}
