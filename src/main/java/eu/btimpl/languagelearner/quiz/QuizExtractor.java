package eu.btimpl.languagelearner.quiz;

/**
 * Reads the questions of a worksheet from its photo. The app only depends on this interface, so a
 * test can replace it and the model behind it can be changed.
 */
public interface QuizExtractor {

  /**
   * Extracts the multiple-choice questions printed on the sheet. The questions are only copied, not
   * answered.
   *
   * @param image the bytes of the photo
   * @param mediaType the media type of the photo, for example {@code image/jpeg}
   * @throws NoQuestionsFoundException when no usable question could be read
   * @throws ExtractionUnavailableException when the model cannot be used right now
   */
  ExtractedQuiz extract(byte[] image, String mediaType);
}
