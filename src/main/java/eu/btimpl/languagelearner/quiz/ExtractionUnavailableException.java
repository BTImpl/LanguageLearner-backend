package eu.btimpl.languagelearner.quiz;

/**
 * The model behind a {@link QuizExtractor} cannot be used right now: it is not set up, too many
 * requests were sent, the quota is used up, the request was blocked for safety reasons, the service
 * could not be reached or it sent an answer that cannot be used. Trying again later may work.
 */
public class ExtractionUnavailableException extends RuntimeException {

  public ExtractionUnavailableException(String message) {
    super(message);
  }

  public ExtractionUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
