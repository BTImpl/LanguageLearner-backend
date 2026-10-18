package eu.btimpl.languagelearner.attempts;

import java.time.Instant;

/**
 * A student as stored in the database.
 *
 * @param id database id
 * @param email lower case address
 * @param name display name, as the sheet had it when the student last signed in
 * @param createdAt when the student first signed in
 */
public record StoredStudent(long id, String email, String name, Instant createdAt) {}
