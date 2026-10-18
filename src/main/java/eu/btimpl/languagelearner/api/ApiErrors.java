package eu.btimpl.languagelearner.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

/** Writes an {@link ApiError} to a response from code that runs outside Spring MVC (filters). */
public final class ApiErrors {

  private ApiErrors() {}

  public static void write(
      HttpServletResponse response, HttpStatus status, String code, String message)
      throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
    response.getWriter().write(toJson(new ApiError(code, message)));
  }

  static String toJson(ApiError error) {
    return "{\"code\":\""
        + escape(error.code())
        + "\",\"message\":\""
        + escape(error.message())
        + "\"}";
  }

  private static String escape(String text) {
    return text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
  }
}
