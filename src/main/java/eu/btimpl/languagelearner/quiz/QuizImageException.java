package eu.btimpl.languagelearner.quiz;

/**
 * The uploaded image cannot be used; says which {@code ApiError} code and HTTP status (400, 413 or
 * 415) to answer.
 */
final class QuizImageException extends RuntimeException {

  private final int status;
  private final String code;

  private QuizImageException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  static QuizImageException missing() {
    return new QuizImageException(
        400,
        "IMAGE_MISSING",
        "Send the photo in the 'image' field of a multipart/form-data request");
  }

  static QuizImageException tooLarge() {
    return new QuizImageException(
        413,
        "IMAGE_TOO_LARGE",
        "The image must be at most " + QuizController.MAX_IMAGE_BYTES / (1024 * 1024) + " MB");
  }

  static QuizImageException unsupportedType() {
    return new QuizImageException(
        415, "IMAGE_TYPE_UNSUPPORTED", "Only JPEG and PNG images are accepted");
  }

  int status() {
    return status;
  }

  String code() {
    return code;
  }
}
