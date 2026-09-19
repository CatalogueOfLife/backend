package life.catalogue.common.tax.authormap;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Looks up a single IPNI author by its exact botanical standard form through the public IPNI search API,
 * e.g. {@code https://www.ipni.org/api/1/search?q=author%20std:Nees&f=f_authors}.
 * IPNI offers no bulk author download, but the API answers one standard form at a time with separate
 * forename and surname fields - which is exactly what the flat full name in the authormap is missing.
 *
 * Every answer, including "not found", is appended to a local TSV cache so an interrupted or repeated run
 * never asks IPNI twice.
 */
public class IpniAuthorLookup implements AutoCloseable {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String ENDPOINT = "https://www.ipni.org/api/1/search";
  private static final long PAUSE_MS = 250;
  private static final int MAX_RETRIES = 4;

  public record IpniAuthor(String standardForm, String forename, String surname) {}

  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
  private final Map<String, Optional<IpniAuthor>> cache = new HashMap<>();
  private final BufferedWriter cacheWriter;
  private int fetched;

  public IpniAuthorLookup(Path cacheFile) throws IOException {
    if (Files.exists(cacheFile)) {
      for (String line : Files.readAllLines(cacheFile, StandardCharsets.UTF_8)) {
        String[] c = line.split("\t", -1);
        if (c.length < 2) continue;
        cache.put(c[0], "1".equals(c[1]) && c.length >= 4 ? Optional.of(new IpniAuthor(c[0], c[2], c[3])) : Optional.empty());
      }
    } else if (cacheFile.getParent() != null) {
      Files.createDirectories(cacheFile.getParent());
    }
    cacheWriter = Files.newBufferedWriter(cacheFile, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
  }

  /** @return the IPNI author with exactly this standard form, or empty if IPNI has none */
  public Optional<IpniAuthor> get(String standardForm) throws Exception {
    if (cache.containsKey(standardForm)) {
      return cache.get(standardForm);
    }
    Optional<IpniAuthor> author = parse(standardForm, fetch(standardForm));
    cache.put(standardForm, author);
    cacheWriter.write(author.map(a -> String.join("\t", standardForm, "1", clean(a.forename()), clean(a.surname())))
      .orElse(standardForm + "\t0"));
    cacheWriter.newLine();
    cacheWriter.flush();
    if (++fetched % 100 == 0) {
      System.out.printf("  ipni: %d authors fetched%n", fetched);
    }
    return author;
  }

  /** Keeps only a result whose standard form is the requested one, the search itself being fuzzy. */
  static Optional<IpniAuthor> parse(String standardForm, JsonNode json) {
    for (JsonNode r : json.path("results")) {
      if (standardForm.equals(r.path("standardForm").asText(null))) {
        return Optional.of(new IpniAuthor(standardForm, r.path("forename").asText(""), r.path("surname").asText("")));
      }
    }
    return Optional.empty();
  }

  private JsonNode fetch(String standardForm) throws Exception {
    String url = ENDPOINT + "?perPage=10&f=f_authors&q=" + URLEncoder.encode("author std:" + standardForm, StandardCharsets.UTF_8);
    Exception last = null;
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      Thread.sleep(PAUSE_MS * attempt);
      try {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
          .header("Accept", "application/json")
          .header("User-Agent", "col-backend-authormap-surnamefix/1.0 (https://www.checklistbank.org)")
          .timeout(Duration.ofMinutes(1)).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) throw new IllegalStateException("IPNI HTTP " + resp.statusCode());
        return MAPPER.readTree(resp.body());
      } catch (Exception e) {
        last = e;
        System.err.println("  ipni fetch " + standardForm + " attempt " + attempt + "/" + MAX_RETRIES + " failed: " + e.getMessage());
      }
    }
    throw last;
  }

  private static String clean(String x) {
    return x == null ? "" : x.replace('\t', ' ').replace('\n', ' ').trim();
  }

  @Override
  public void close() throws IOException {
    cacheWriter.close();
  }
}
