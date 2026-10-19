package life.catalogue.release.review;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The GBIF impact reports of an extended release, as published by GBIF at download.gbif.org.
 *
 * GBIF matches its occurrences against a COL XR candidate and lists every distinct verbatim classification whose
 * interpretation would change, with its occurrence count - "current" is what GBIF does today, "proposed" what the
 * candidate would do. The same files drive the GBIF Impact Tool of ChecklistBank, which finds them through the
 * {@code index.json} next to them. A release usually has several, one per scope (alltaxa, coleoptera, fabaceae,
 * fossils), each with its own minimum occurrence count. GBIF only builds them for extended releases, and only
 * some time after the release exists, so finding none is normal.
 *
 * Which release "current" is, GBIF's species match metadata tells: the ChecklistBank key of the COL XR its
 * production matching index was built from.
 */
public class GbifImpactReports {
  static final String INDEX = "index.json";
  private static final ObjectMapper OM = new ObjectMapper();
  /** report-{colKey}-{scope}-{yyyy-MM-dd}.txt */
  private static final Pattern FILENAME = Pattern.compile("^report-\\d+-(.+)-\\d{4}-\\d{2}-\\d{2}\\.[a-z]+$");

  public record Report(String filename, String scope, String name, String description, String created) {}

  /**
   * The release GBIF interprets its occurrences with today, the "current" side of every impact report.
   */
  public record Current(int releaseKey, @Nullable String alias, @Nullable String created) {}

  private final URI base;
  private final @Nullable URI metadata;
  private final CloseableHttpClient http;

  /**
   * @param base     the directory holding index.json and the reports
   * @param metadata the GBIF species match metadata of the COL XR checklist, null if unknown
   */
  public GbifImpactReports(URI base, @Nullable URI metadata, CloseableHttpClient http) {
    this.base = base.toString().endsWith("/") ? base : URI.create(base + "/");
    this.metadata = metadata;
    this.http = http;
  }

  /**
   * @return the release GBIF matches against in production, or null if no metadata location is configured
   */
  @Nullable
  public Current current() throws IOException {
    if (metadata == null) {
      return null;
    }
    return current(OM.readTree(get(metadata)));
  }

  static Current current(JsonNode metadata) throws IOException {
    JsonNode main = metadata.path("mainIndex");
    String key = main.path("clbDatasetKey").asText("");
    if (!key.matches("\\d+")) {
      throw new IOException("the GBIF match metadata names no ChecklistBank release");
    }
    return new Current(Integer.parseInt(key), main.path("datasetAlias").asText(null), metadata.path("created").asText(null));
  }

  /**
   * @return the newest report of every scope GBIF published for the given release, in scope order
   */
  public List<Report> list(int releaseKey) throws IOException {
    return select(OM.readTree(get(base.resolve(INDEX))), releaseKey);
  }

  public byte[] download(Report report, long maxBytes) throws IOException {
    byte[] content = get(base.resolve(report.filename()));
    if (content.length > maxBytes) {
      throw new IOException("too big, " + content.length + " bytes");
    }
    return content;
  }

  /**
   * A scope generated twice for one release is only taken in its newest version - it is GBIF regenerating it,
   * and the older one compares against an older "current".
   */
  static List<Report> select(JsonNode index, int releaseKey) {
    Map<String, Report> byScope = new LinkedHashMap<>();
    for (JsonNode n : index.path("reports")) {
      if (!String.valueOf(releaseKey).equals(n.path("colKey").asText())) {
        continue;
      }
      String filename = n.path("filename").asText(null);
      // the filename is later resolved against the base URI and used as a mount path
      if (filename == null || filename.contains("/") || filename.contains("..")) {
        continue;
      }
      Matcher m = FILENAME.matcher(filename);
      var r = new Report(filename, m.matches() ? m.group(1) : filename,
        n.path("name").asText(""), n.path("description").asText(""), n.path("created").asText(""));
      byScope.merge(r.scope(), r, (a, b) -> b.created().compareTo(a.created()) > 0 ? b : a);
    }
    List<Report> reports = new ArrayList<>(byScope.values());
    reports.sort(Comparator.comparing(Report::scope));
    return reports;
  }

  protected byte[] get(URI uri) throws IOException {
    return http.execute(new HttpGet(uri), (HttpClientResponseHandler<byte[]>) resp -> {
      if (resp.getCode() / 100 != 2) {
        throw new IOException("HTTP " + resp.getCode() + " for " + uri);
      }
      HttpEntity e = resp.getEntity();
      if (e == null) {
        return new byte[0];
      }
      try (InputStream in = e.getContent(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        in.transferTo(out);
        return out.toByteArray();
      }
    });
  }
}
