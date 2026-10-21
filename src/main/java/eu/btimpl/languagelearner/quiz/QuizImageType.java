package eu.btimpl.languagelearner.quiz;

import java.util.Optional;

/**
 * The image types the app accepts. The type is read from the first bytes of the file, not from the
 * {@code Content-Type} the client declared, because that is only a claim and the model needs the
 * real media type.
 */
enum QuizImageType {
  JPEG("image/jpeg"),
  PNG("image/png");

  private static final byte[] JPEG_SIGNATURE = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
  private static final byte[] PNG_SIGNATURE = {
    (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
  };

  private final String mediaType;

  QuizImageType(String mediaType) {
    this.mediaType = mediaType;
  }

  String mediaType() {
    return mediaType;
  }

  /** The type of the image, or nothing when it is neither a JPEG nor a PNG. */
  static Optional<QuizImageType> detect(byte[] bytes) {
    if (startsWith(bytes, JPEG_SIGNATURE)) {
      return Optional.of(JPEG);
    }
    if (startsWith(bytes, PNG_SIGNATURE)) {
      return Optional.of(PNG);
    }
    return Optional.empty();
  }

  private static boolean startsWith(byte[] bytes, byte[] signature) {
    if (bytes.length < signature.length) {
      return false;
    }
    for (int i = 0; i < signature.length; i++) {
      if (bytes[i] != signature[i]) {
        return false;
      }
    }
    return true;
  }
}
