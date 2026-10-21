package eu.btimpl.languagelearner.quiz;

import java.util.List;

/**
 * A quiz read from the photo of a worksheet. It only holds what is printed on the sheet: which
 * option is correct is not known.
 *
 * @param title the exercise heading of the sheet, or {@code null} when it has none
 * @param questions the usable questions, in the order of the sheet; never empty when produced by a
 *     {@link QuizExtractor}
 * @param droppedQuestions how many questions the model returned that were left out because they
 *     were incomplete
 */
public record ExtractedQuiz(String title, List<Question> questions, int droppedQuestions) {

  public ExtractedQuiz {
    questions = List.copyOf(questions);
  }

  /**
   * One question with its options.
   *
   * @param number the number printed next to the question (or its position when none is printed)
   * @param text the text of the question as printed, blanks included
   * @param options the answer options, 2 to 5 of them, in the order of the sheet
   */
  public record Question(int number, String text, List<Option> options) {

    public Question {
      options = List.copyOf(options);
    }
  }

  /**
   * One answer option.
   *
   * @param label the label as printed ({@code a}, {@code b}, ... or {@code 1}, {@code 2}, ...)
   * @param text the text of the option as printed
   */
  public record Option(String label, String text) {}
}
