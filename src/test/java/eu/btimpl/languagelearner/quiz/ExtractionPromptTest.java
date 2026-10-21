package eu.btimpl.languagelearner.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExtractionPromptTest {

  @Test
  void forbidsAnsweringTheQuestions() {
    String prompt = ExtractionPrompt.text();

    assertThat(prompt).contains("Do NOT answer the questions");
    assertThat(prompt).contains("Do NOT mark, suggest or hint at a correct option");
  }

  @Test
  void forbidsGuessingWhatCannotBeRead() {
    assertThat(ExtractionPrompt.text()).contains("Never guess or invent text");
  }
}
