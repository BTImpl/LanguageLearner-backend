package eu.btimpl.languagelearner.api;

/**
 * The body of every error response.
 *
 * @param code a stable, machine readable code such as {@code STUDENT_NOT_FOUND}
 * @param message a short explanation for people
 */
public record ApiError(String code, String message) {}
