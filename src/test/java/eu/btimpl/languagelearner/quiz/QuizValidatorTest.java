package eu.btimpl.languagelearner.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import eu.btimpl.languagelearner.quiz.ModelQuiz.ModelOption;
import eu.btimpl.languagelearner.quiz.ModelQuiz.ModelQuestion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuizValidatorTest {

  private static ModelOption option(String label, String text) {
    return new ModelOption(label, text);
  }

  private static ModelQuestion question(Integer number, String text, ModelOption... options) {
    return new ModelQuestion(number, text, Arrays.asList(options));
  }

  private static ModelQuestion good(int number) {
    return question(number, "She ___ to the airport.", option("a", "go"), option("b", "went"));
  }

  private static ModelQuiz quiz(String title, ModelQuestion... questions) {
    return new ModelQuiz(title, Arrays.asList(questions));
  }

  @Test
  void keepsACompleteQuestionAndStripsItsTexts() {
    ExtractedQuiz result =
        QuizValidator.validate(
            quiz(
                " Unit 5 ",
                question(1, " She ___ there. ", option(" a ", " go "), option("b", "went"))));

    assertThat(result.title()).isEqualTo("Unit 5");
    assertThat(result.droppedQuestions()).isZero();
    assertThat(result.questions()).hasSize(1);
    ExtractedQuiz.Question first = result.questions().get(0);
    assertThat(first.number()).isEqualTo(1);
    assertThat(first.text()).isEqualTo("She ___ there.");
    assertThat(first.options())
        .containsExactly(
            new ExtractedQuiz.Option("a", "go"), new ExtractedQuiz.Option("b", "went"));
  }

  @Test
  void aBlankOrMissingTitleIsNull() {
    assertThat(QuizValidator.validate(quiz("  ", good(1))).title()).isNull();
    assertThat(QuizValidator.validate(quiz(null, good(1))).title()).isNull();
  }

  @Test
  void keepsTheOrderOfTheSheet() {
    ExtractedQuiz result = QuizValidator.validate(quiz(null, good(3), good(1), good(2)));

    assertThat(result.questions())
        .extracting(ExtractedQuiz.Question::number)
        .containsExactly(3, 1, 2);
  }

  @Test
  void dropsAndCountsTheQuestionsThatAreNotComplete() {
    List<ModelQuestion> bad =
        new ArrayList<>(
            Arrays.asList(
                question(null, "No number", option("a", "x"), option("b", "y")),
                question(2, " ", option("a", "x"), option("b", "y")),
                question(3, null, option("a", "x"), option("b", "y")),
                question(4, "One option", option("a", "x")),
                question(5, "No options"),
                new ModelQuestion(6, "Options missing", null),
                question(
                    7,
                    "Six options",
                    option("a", "1"),
                    option("b", "2"),
                    option("c", "3"),
                    option("d", "4"),
                    option("e", "5"),
                    option("f", "6")),
                question(8, "Blank option text", option("a", "x"), option("b", " ")),
                question(9, "Blank label", option("a", "x"), option(" ", "y")),
                question(10, "Missing label", option("a", "x"), option(null, "y")),
                null));

    ModelQuiz mixed = new ModelQuiz("T", new ArrayList<>(bad));
    mixed.questions().add(good(11));

    ExtractedQuiz result = QuizValidator.validate(mixed);

    assertThat(result.questions()).extracting(ExtractedQuiz.Question::number).containsExactly(11);
    assertThat(result.droppedQuestions()).isEqualTo(bad.size());
  }

  @Test
  void allowsBetweenTwoAndFiveOptions() {
    ExtractedQuiz result =
        QuizValidator.validate(
            quiz(
                null,
                question(1, "Two", option("a", "x"), option("b", "y")),
                question(
                    2,
                    "Five",
                    option("1", "a"),
                    option("2", "b"),
                    option("3", "c"),
                    option("4", "d"),
                    option("5", "e"))));

    assertThat(result.questions()).hasSize(2);
    assertThat(result.droppedQuestions()).isZero();
  }

  @Test
  void aMissingQuestionListGivesAnEmptyQuiz() {
    ExtractedQuiz result = QuizValidator.validate(new ModelQuiz("T", null));

    assertThat(result.questions()).isEmpty();
    assertThat(result.droppedQuestions()).isZero();
  }
}
