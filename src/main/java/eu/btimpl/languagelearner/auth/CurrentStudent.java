package eu.btimpl.languagelearner.auth;

import eu.btimpl.languagelearner.sheets.Student;

/**
 * The signed-in student, found in the teacher's sheet by the e-mail address of the token. Take it
 * in a controller with {@code @AuthenticationPrincipal CurrentStudent student}; the endpoints work
 * from it alone and never take a student id from the request.
 */
public record CurrentStudent(String email, String name, String tab) {

  static CurrentStudent of(Student student) {
    return new CurrentStudent(student.email(), student.name(), student.tab());
  }
}
