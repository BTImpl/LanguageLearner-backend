package eu.btimpl.languagelearner.auth;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Who the signed-in student is. */
@RestController
@RequestMapping("/api/v1")
class MeController {

  @GetMapping("/me")
  Me me(@AuthenticationPrincipal CurrentStudent student) {
    return new Me(student.email(), student.name());
  }

  /** The student as the app sees them. */
  public record Me(String email, String name) {}
}
