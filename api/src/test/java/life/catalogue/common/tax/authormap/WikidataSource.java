package life.catalogue.common.tax.authormap;

import com.fasterxml.jackson.databind.*;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

public class WikidataSource implements AuthorSource {
  private static final String ENDPOINT = "https://query.wikidata.org/sparql";
  // authors with a botanist abbreviation (P428) and/or a zoologist author citation (P835)
  private static final String QUERY = """
    SELECT ?person ?name ?botAbbr ?zooAuthor WHERE {
      { ?person wdt:P428 ?botAbbr. } UNION { ?person wdt:P835 ?zooAuthor. }
      OPTIONAL { ?person wdt:P428 ?botAbbr. }
      OPTIONAL { ?person wdt:P835 ?zooAuthor. }
      ?person rdfs:label ?name. FILTER(LANG(?name) = "en")
    }""";

  @Override public String name() { return "wikidata"; }

  @Override public List<AuthorEntry> read() throws Exception {
    String url = ENDPOINT + "?format=json&query=" + URLEncoder.encode(QUERY, StandardCharsets.UTF_8);
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    HttpRequest req = HttpRequest.newBuilder(URI.create(url))
      .header("Accept", "application/sparql-results+json")
      .header("User-Agent", "col-backend-authormap-generator/1.0 (https://www.checklistbank.org)")
      .timeout(Duration.ofMinutes(3)).GET().build();
    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (resp.statusCode() != 200) throw new IllegalStateException("Wikidata SPARQL HTTP " + resp.statusCode());
    return parse(new ObjectMapper().readTree(resp.body()));
  }

  /** Pure, unit-testable parse of the SPARQL JSON result. One AuthorEntry per person IRI. */
  public List<AuthorEntry> parse(JsonNode json) {
    Map<String, List<String>> aliasesByPerson = new LinkedHashMap<>();
    Map<String, String> nameByPerson = new HashMap<>();
    Map<String, Boolean> hasBot = new HashMap<>();
    Map<String, Boolean> hasZoo = new HashMap<>();

    for (JsonNode b : json.path("results").path("bindings")) {
      String person = b.path("person").path("value").asText(null);
      if (person == null) continue;
      List<String> aliases = aliasesByPerson.computeIfAbsent(person, k -> new ArrayList<>());
      String name = text(b, "name");
      if (name != null) { nameByPerson.put(person, name); addUnique(aliases, name); }
      String bot = text(b, "botAbbr");
      if (bot != null) { addUnique(aliases, bot); hasBot.put(person, true); }
      String zoo = text(b, "zooAuthor");
      if (zoo != null) { addUnique(aliases, zoo); hasZoo.put(person, true); }
    }

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

  private static String text(JsonNode b, String field) {
    JsonNode n = b.path(field).path("value");
    return n.isMissingNode() ? null : n.asText();
  }
  private static void addUnique(List<String> list, String v) { if (!list.contains(v)) list.add(v); }
}
