package eu.btimpl.languagelearner.quiz;

import eu.btimpl.languagelearner.quiz.ModelQuiz.ModelOption;
import eu.btimpl.languagelearner.quiz.ModelQuiz.ModelQuestion;
import java.util.ArrayList;
import java.util.List;

/**
 * Checks what the model returned, even though its output is bound to a schema: a schema cannot say
 * that a text must not be blank or how many options a question has. A question that is not complete
 * is dropped and counted, never repaired.
 */
final class QuizValidator {

  static final int MIN_OPTIONS = 2;
  static final int MAX_OPTIONS = 5;

  private QuizValidator() {}

  /** The usable part of the model's quiz; its question list may be empty. */
  static ExtractedQuiz validate(ModelQuiz quiz) {
    List<ExtractedQuiz.Question> questions = new ArrayList<>();
    int dropped = 0;
    List<ModelQuestion> raw = quiz.questions() == null ? List.of() : quiz.questions();
    for (ModelQuestion question : raw) {
      ExtractedQuiz.Question valid = validate(question);
      if (valid == null) {
        dropped++;
      } else {
        questions.add(valid);
      }
    }
    String title = quiz.title() == null || quiz.title().isBlank() ? null : quiz.title().strip();
    return new ExtractedQuiz(title, questions, dropped);
  }

  private static ExtractedQuiz.Question validate(ModelQuestion question) {
    if (question == null
        || question.number() == null
        || isBlank(question.text())
        || question.options() == null
        || question.options().size() < MIN_OPTIONS
        || question.options().size() > MAX_OPTIONS) {
      return null;
    }
    List<ExtractedQuiz.Option> options = new ArrayList<>();
    for (ModelOption option : question.options()) {
      if (option == null || isBlank(option.label()) || isBlank(option.text())) {
        return null;
      }
      options.add(new ExtractedQuiz.Option(option.label().strip(), option.text().strip()));
    }
    return new ExtractedQuiz.Question(question.number(), question.text().strip(), options);
  }

  private static boolean isBlank(String text) {
    return text == null || text.isBlank();
  }
}
