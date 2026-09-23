package life.catalogue.matching.person.harvest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.function.Predicate;

/**
 * Keeps every answer on disk under the SHA-1 of its URL, so a harvest that dies halfway resumes where it stopped.
 * Only answers that pass the check are kept, and a kept one that fails it is fetched again.
 * Delete the directory for a fresh harvest.
 */
public class CachingFetcher implements Fetcher {
  private final Path dir;
  private final Fetcher delegate;
  private final Predicate<String> valid;

  public CachingFetcher(Path dir, Fetcher delegate) {
    this(dir, delegate, body -> true);
  }

  public CachingFetcher(Path dir, Fetcher delegate, Predicate<String> valid) {
    this.dir = dir;
    this.delegate = delegate;
    this.valid = valid;
  }

  @Override
  public String get(String url) throws Exception {
    Path f = dir.resolve(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(url.getBytes(StandardCharsets.UTF_8))));
    if (Files.exists(f)) {
      String cached = Files.readString(f, StandardCharsets.UTF_8);
      if (valid.test(cached)) {
        return cached;
      }
    }
    String body = delegate.get(url);
    if (!valid.test(body)) {
      throw new IllegalStateException("incomplete answer, not cached: " + url);
    }
    Files.createDirectories(dir);
    Path tmp = Files.createTempFile(dir, "fetch", ".tmp");
    Files.writeString(tmp, body, StandardCharsets.UTF_8);
    Files.move(tmp, f, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    return body;
  }
}
