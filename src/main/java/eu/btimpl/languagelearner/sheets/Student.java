package eu.btimpl.languagelearner.sheets;

/**
 * A student of the teacher's sheet.
 *
 * @param email the student's Google account, as written in the sheet
 * @param name display name
 * @param tab name of the tab with this student's words
 */
public record Student(String email, String name, String tab) {}
