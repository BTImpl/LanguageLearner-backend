package eu.btimpl.languagelearner.quiz;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** The instructions sent to the model. They live in a file next to the code, not in a string. */
final class ExtractionPrompt {

  static final String RESOURCE = "/quiz/extraction-prompt.txt";

  private ExtractionPrompt() {}

  /** The text of the prompt. */
  static String text() {
    try (InputStream in = ExtractionPrompt.class.getResourceAsStream(RESOURCE)) {
      if (in == null) {
        throw new IllegalStateException("Missing resource " + RESOURCE);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
