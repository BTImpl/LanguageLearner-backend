package eu.btimpl.languagelearner.auth;

import eu.btimpl.languagelearner.api.ApiErrors;
import eu.btimpl.languagelearner.attempts.StudentRegistry;
import eu.btimpl.languagelearner.sheets.SheetsException;
import eu.btimpl.languagelearner.sheets.Student;
import eu.btimpl.languagelearner.sheets.StudentDirectory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs right after the token has been verified. Looks the student up in the teacher's sheet by the
 * e-mail address of the token: a student found becomes the principal of the request, an address
 * that is not in the sheet is answered with 403 {@code STUDENT_NOT_FOUND}.
 *
 * <p>Every request with a token is checked except the health endpoint, which stays open, so a
 * Google account that is not on the list cannot read anything. It is not a bean on purpose: a
 * filter bean would also be added to the servlet container and run a second time.
 *
 * <p>A student seen for the first time is also stored in the database. That is best effort: when
 * the database is down the request goes on, since the words come from the sheet alone.
 */
public class StudentLookupFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(StudentLookupFilter.class);

  private final StudentDirectory directory;
  private final StudentRegistry registry;

  public StudentLookupFilter(StudentDirectory directory, StudentRegistry registry) {
    this.directory = directory;
    this.registry = registry;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith("/actuator/health");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
      Optional<Student> student;
      try {
        student = directory.findByEmail(jwtAuthentication.getToken().getClaimAsString("email"));
      } catch (SheetsException e) {
        log.error("The students could not be read from the spreadsheet", e);
        ApiErrors.write(
            response,
            HttpStatus.SERVICE_UNAVAILABLE,
            "SHEET_UNAVAILABLE",
            "The word list is not available right now");
        return;
      }
      if (student.isEmpty()) {
        ApiErrors.write(
            response,
            HttpStatus.FORBIDDEN,
            "STUDENT_NOT_FOUND",
            "This account is not on the student list");
        return;
      }
      register(student.get());
      SecurityContextHolder.getContext()
          .setAuthentication(
              new StudentAuthentication(
                  CurrentStudent.of(student.get()), jwtAuthentication.getToken()));
    }
    chain.doFilter(request, response);
  }

  private void register(Student student) {
    try {
      registry.register(student);
    } catch (DataAccessException e) {
      log.warn("The student could not be stored in the database, going on without", e);
    }
  }
}
