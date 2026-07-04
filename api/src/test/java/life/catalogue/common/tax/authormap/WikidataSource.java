package life.catalogue.common.tax.authormap;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/**
 * Fetches botanist author abbreviations (P428) and zoologist author citations (P835) from Wikidata.
 * The full result set is far too large for a single WDQS request (responses truncate), so each property
 * is fetched in ORDER BY ?person / LIMIT / OFFSET pages, with per-page retries, and accumulated by person.
 */
public class WikidataSource implements AuthorSource {
  // Wikidata labels occasionally contain raw control characters (unescaped newlines) that strict JSON rejects.
  private static final ObjectMapper MAPPER =
    JsonMapper.builder().enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS).build();
  private static final String ENDPOINT = "https://query.wikidata.org/sparql";
  private static final int BATCH = 5000;
  private static final int MAX_RETRIES = 4;
  private static final int MAX_OFFSET = 200000;      // safety bound if a short page is never reached
  private static final int MAX_CONSECUTIVE_FAILS = 3; // give up on a property after this many unparseable pages in a row

  @Override public String name() { return "wikidata"; }

  @Override public List<AuthorEntry> read() throws Exception {
    Ctx ctx = new Ctx();
    fetchProperty("P428", "botAbbr", ctx);   // botanist author abbreviation
    fetchProperty("P835", "zooAuthor", ctx); // zoologist author citation
    return ctx.build();
  }

  /**
   * Pages through one property. A page that cannot be fetched/parsed after retries (WDQS occasionally
   * emits a malformed record) is skipped with a logged gap rather than aborting the whole run.
   */
  private void fetchProperty(String prop, String var, Ctx ctx) {
    int offset = 0, consecutiveFails = 0, skipped = 0;
    while (offset < MAX_OFFSET) {
      String q = "SELECT ?person ?name ?" + var + " WHERE {"
        + " ?person wdt:" + prop + " ?" + var + "."
        + " ?person rdfs:label ?name. FILTER(LANG(?name) = \"en\")"
        + " } ORDER BY ?person LIMIT " + BATCH + " OFFSET " + offset;
      int rows;
      try {
        rows = ctx.accumulate(fetch(q));
        consecutiveFails = 0;
        System.out.printf("  wikidata %s offset %d: %d rows%n", prop, offset, rows);
      } catch (Exception e) {
        skipped++;
        if (++consecutiveFails >= MAX_CONSECUTIVE_FAILS) {
          System.err.printf("  wikidata %s: giving up at offset %d after %d consecutive failed pages%n", prop, offset, consecutiveFails);
          break;
        }
        System.err.printf("  wikidata %s offset %d: SKIPPED page after retries (%s)%n", prop, offset, e.getMessage());
        offset += BATCH;
        continue;
      }
      if (rows < BATCH) break;
      offset += BATCH;
    }
    if (skipped > 0) {
      System.err.printf("  wikidata %s: %d page(s) skipped -> up to %d authors omitted%n", prop, skipped, skipped * BATCH);
    }
  }

  private JsonNode fetch(String query) throws Exception {
    String url = ENDPOINT + "?format=json&query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    Exception last = null;
    for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
      try {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
          .header("Accept", "application/sparql-results+json")
          .header("User-Agent", "col-backend-authormap-generator/1.0 (https://www.checklistbank.org)")
          .timeout(Duration.ofMinutes(3)).GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() != 200) throw new IllegalStateException("Wikidata SPARQL HTTP " + resp.statusCode());
        return MAPPER.readTree(resp.body());
      } catch (Exception e) {
        last = e;
        System.err.println("  wikidata fetch attempt " + attempt + "/" + MAX_RETRIES + " failed: " + e.getMessage());
      }
    }
    throw last;
  }

  /** Pure, unit-testable parse of one SPARQL JSON result page. */
  public List<AuthorEntry> parse(JsonNode json) {
    Ctx ctx = new Ctx();
    ctx.accumulate(json);
    return ctx.build();
  }

  /** Accumulates bindings across pages and properties into one AuthorEntry per person IRI. */
  static final class Ctx {
    final Map<String, List<String>> aliasesByPerson = new LinkedHashMap<>();
    final Map<String, String> nameByPerson = new HashMap<>();
    final Map<String, Boolean> hasBot = new HashMap<>();
    final Map<String, Boolean> hasZoo = new HashMap<>();

    /** @return the number of binding rows on this page (for pagination termination). */
    int accumulate(JsonNode json) {
      int rows = 0;
      for (JsonNode b : json.path("results").path("bindings")) {
        rows++;
        String person = b.path("person").path("value").asText(null);
        if (person == null) continue;
        List<String> aliases = aliasesByPerson.computeIfAbsent(person, k -> new ArrayList<>());
        String name = text(b, "name");
        if (name != null) { nameByPerson.put(person, name); addUnique(aliases, name); }
        // Botanical abbreviations (P428) are deliberately NOT imported as keys: IPNI already covers them and
        // Wikidata's variants collide with the curated botanical matching logic. We only record the botanist
        // status (for the code) and keep the full-name label as an alias.
        String bot = text(b, "botAbbr");
        if (bot != null) { hasBot.put(person, true); }
        // Zoological citations (P835) ARE the form used in zoological names, so they are imported (ambiguous
        // ones are dropped later by the merger's disambiguation). Suffix forms are still skipped.
        String zoo = text(b, "zooAuthor");
        if (zoo != null) { if (!isSuffixed(zoo)) addUnique(aliases, zoo); hasZoo.put(person, true); }
      }
      return rows;
    }

    List<AuthorEntry> build() {
      List<AuthorEntry> out = new ArrayList<>();
      for (var e : aliasesByPerson.entrySet()) {
        String person = e.getKey();
        List<String> aliases = e.getValue();
        if (aliases.isEmpty()) continue;
        boolean bot = hasBot.getOrDefault(person, false);
        boolean zoo = hasZoo.getOrDefault(person, false);
        AuthorCode code = (bot && zoo) ? AuthorCode.ANY : (zoo ? AuthorCode.ZOO : AuthorCode.BOT);
        String canonical = nameByPerson.getOrDefault(person, aliases.get(0));
        out.add(new AuthorEntry(canonical, code, aliases));
      }
      return out;
    }
  }

  private static String text(JsonNode b, String field) {
    JsonNode n = b.path(field).path("value");
    return n.isMissingNode() ? null : n.asText();
  }
  private static void addUnique(List<String> list, String v) { if (!list.contains(v)) list.add(v); }

  // Wikidata sometimes stores a disambiguated abbreviation that embeds a nomenclatural suffix such as
  // "F.R.Jones bis", "A.M.Sm.bis" or "Hirats. f." (filius). Importing these as plain keys would erase the
  // suffix distinction (making "Jones bis"/"Hirats. f." collapse onto the base author), so we skip the
  // abbreviation while still recording the author. Curated (IPNI) suffix forms like "L.f." are unaffected.
  private static final java.util.regex.Pattern SUFFIXED =
    java.util.regex.Pattern.compile("(^|[\\s.])(bis|ter|filius|fil|f)\\.?$", java.util.regex.Pattern.CASE_INSENSITIVE);
  private static boolean isSuffixed(String v) { return SUFFIXED.matcher(v).find(); }
}
