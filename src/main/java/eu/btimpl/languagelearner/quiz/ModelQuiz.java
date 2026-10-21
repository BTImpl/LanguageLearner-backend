package eu.btimpl.languagelearner.quiz;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The quiz exactly as the model returned it, before it is checked. Anything may be missing here;
 * {@link QuizValidator} decides what is usable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record ModelQuiz(String title, List<ModelQuestion> questions) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ModelQuestion(Integer number, String text, List<ModelOption> options) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  record ModelOption(String label, String text) {}
}
