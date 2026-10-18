package eu.btimpl.languagelearner.sheets;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Stable identifiers derived from names, so a reread of the sheet gives the same ids. */
final class Ids {

  private static final int ID_BYTES = 8;

  private Ids() {}

  /** A 16 character hex id from the first 8 bytes of the SHA-256 of the parts. */
  static String of(String... parts) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(String.join("\0", parts).getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash, 0, ID_BYTES);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is required to exist on every Java platform.
      throw new IllegalStateException(e);
    }
  }
}
