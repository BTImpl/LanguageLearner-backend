package eu.btimpl.languagelearner.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class QuizImageTypeTest {

  @Test
  void recognisesAJpegByItsFirstBytes() {
    byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00};

    assertThat(QuizImageType.detect(jpeg)).contains(QuizImageType.JPEG);
    assertThat(QuizImageType.JPEG.mediaType()).isEqualTo("image/jpeg");
  }

  @Test
  void recognisesAPngByItsFirstBytes() {
    byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};

    assertThat(QuizImageType.detect(png)).contains(QuizImageType.PNG);
    assertThat(QuizImageType.PNG.mediaType()).isEqualTo("image/png");
  }

  @Test
  void rejectsEverythingElse() {
    assertThat(QuizImageType.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII)))
        .isEqualTo(Optional.empty());
    assertThat(QuizImageType.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).isEmpty();
    assertThat(QuizImageType.detect("<html></html>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
  }

  @Test
  void rejectsFilesThatAreTooShortToTell() {
    assertThat(QuizImageType.detect(new byte[0])).isEmpty();
    assertThat(QuizImageType.detect(new byte[] {(byte) 0xFF, (byte) 0xD8})).isEmpty();
    assertThat(QuizImageType.detect(new byte[] {(byte) 0x89, 'P', 'N', 'G'})).isEmpty();
  }
}
