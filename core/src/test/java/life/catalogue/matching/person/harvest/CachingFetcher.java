package life.catalogue.matching.person.harvest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Keeps every answer on disk under the SHA-1 of its URL, so a harvest that dies halfway resumes where it stopped.
 * Delete the directory for a fresh harvest.
 */
public class CachingFetcher implements Fetcher {
  private final Path dir;
  private final Fetcher delegate;

  public CachingFetcher(Path dir, Fetcher delegate) {
    this.dir = dir;
    this.delegate = delegate;
  }

  @Override
  public String get(String url) throws Exception {
    Path f = dir.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8))));
    if (Files.exists(f)) {
      return Files.readString(f, StandardCharsets.UTF_8);
    }
    String body = delegate.get(url);
    Files.createDirectories(dir);
    Path tmp = Files.createTempFile(dir, "fetch", ".tmp");
    Files.writeString(tmp, body, StandardCharsets.UTF_8);
    Files.move(tmp, f, StandardCopyOption.ATOMIC_MOVE);
    return body;
  }
}
