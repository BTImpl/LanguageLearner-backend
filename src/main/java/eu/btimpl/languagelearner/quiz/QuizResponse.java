package eu.btimpl.languagelearner.quiz;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** The quiz read from a photo, as the app receives it. */
@Schema(name = "Quiz")
public record QuizResponse(
    @Schema(
            description = "The exercise heading of the sheet; null when the sheet has none",
            nullable = true,
            example = "Unit 5 - Travel")
        String title,
    @Schema(description = "The questions in the order of the sheet; never empty")
        List<QuestionResponse> questions,
    @Schema(
            description =
                "How many questions the model returned that were left out because they were "
                    + "incomplete",
            example = "0")
        int skippedQuestions) {

  static QuizResponse of(ExtractedQuiz quiz) {
    return new QuizResponse(
        quiz.title(),
        quiz.questions().stream().map(QuestionResponse::of).toList(),
        quiz.droppedQuestions());
  }

  /** One question. The correct option is not known, so none is marked. */
  @Schema(name = "QuizQuestion")
  public record QuestionResponse(
      @Schema(description = "The number printed next to the question", example = "1") int number,
      @Schema(description = "The question as printed", example = "She ___ to the airport.")
          String text,
      @Schema(description = "The answer options, 2 to 5, in the order of the sheet")
          List<OptionResponse> options) {

    static QuestionResponse of(ExtractedQuiz.Question question) {
      return new QuestionResponse(
          question.number(),
          question.text(),
          question.options().stream().map(OptionResponse::of).toList());
    }
  }

  /** One answer option. */
  @Schema(name = "QuizOption")
  public record OptionResponse(
      @Schema(description = "The label as printed", example = "a") String label,
      @Schema(description = "The option as printed", example = "went") String text) {

    static OptionResponse of(ExtractedQuiz.Option option) {
      return new OptionResponse(option.label(), option.text());
    }
  }
}
