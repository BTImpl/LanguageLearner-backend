package eu.btimpl.languagelearner.auth;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** A request made by a known student: the principal is the {@link CurrentStudent}. */
final class StudentAuthentication extends AbstractAuthenticationToken {

  private final CurrentStudent student;
  private final Jwt token;

  StudentAuthentication(CurrentStudent student, Jwt token) {
    super(List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    this.student = student;
    this.token = token;
    setAuthenticated(true);
  }

  @Override
  public Object getPrincipal() {
    return student;
  }

  @Override
  public Object getCredentials() {
    return token;
  }
}
