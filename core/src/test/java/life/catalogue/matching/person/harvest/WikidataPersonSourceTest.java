package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.person.FormCode;
import life.catalogue.matching.person.NameKind;
import life.catalogue.matching.person.RelationType;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.IntStream;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;

public class WikidataPersonSourceTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static JsonNode rows(String... rows) throws Exception {
    return MAPPER.readTree(sparql(rows));
  }

  static String sparql(String... rows) {
    return "{\"results\":{\"bindings\":[" + String.join(",", rows) + "]}}";
  }

  static String id(String q, String v) {
    return "{\"person\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/" + q + "\"},\"v\":{\"type\":\"literal\",\"value\":\"" + v + "\"}}";
  }

  static String time(String p, String time) {
    return claim(p, "{\"time\":\"" + time + "\",\"precision\":11}", "normal");
  }

  static String item(String p, String q) {
    return claim(p, "{\"entity-type\":\"item\",\"id\":\"" + q + "\"}", "normal");
  }

  static String claim(String p, String value, String rank) {
    return "{\"p\":\"" + p + "\",\"mainsnak\":{\"snaktype\":\"value\",\"property\":\"" + p + "\",\"datavalue\":{\"value\":" + value
      + "}},\"rank\":\"" + rank + "\"}";
  }

  /** claims grouped by property, the way wbgetentities has them */
  static String claims(String... claims) {
    java.util.Map<String, java.util.List<String>> byProp = new java.util.LinkedHashMap<>();
    for (String c : claims) {
      String p = c.substring(6, c.indexOf('"', 6));
      byProp.computeIfAbsent(p, k -> new java.util.ArrayList<>()).add(c);
    }
    return byProp.entrySet().stream().map(e -> "\"" + e.getKey() + "\":[" + String.join(",", e.getValue()) + "]")
      .collect(java.util.stream.Collectors.joining(",", "{", "}"));
  }

  static String entities(String... entities) {
    return "{\"entities\":{" + String.join(",", entities) + "}}";
  }

  static String entity(String q, String label, String... aliases) {
    return entityWithClaims(q, null, label, aliases);
  }

  static String entityWithClaims(String q, String claims, String label, String... aliases) {
    StringBuilder sb = new StringBuilder("\"" + q + "\":{\"id\":\"" + q + "\"");
    if (claims != null) sb.append(",\"claims\":").append(claims);
    if (label != null) sb.append(",\"labels\":{\"en\":{\"language\":\"en\",\"value\":\"").append(label).append("\"}}");
    if (aliases.length > 0) {
      sb.append(",\"aliases\":{\"en\":[");
      for (int i = 0; i < aliases.length; i++) {
        sb.append(i == 0 ? "" : ",").append("{\"language\":\"en\",\"value\":\"").append(aliases[i]).append("\"}");
      }
      sb.append("]}");
    }
    return sb.append("}").toString();
  }

  @Test
  public void idsStatementsAndLabels() throws Exception {
    Map<String, PersonRecord.Builder> persons = new TreeMap<>();
    assertEquals(1, WikidataPersonSource.addIds(rows(id("Q2", "9936-1")), WikidataPersonSource.IdProperty.P586, persons));
    WikidataPersonSource.addIds(rows(id("Q2", "G.B.Sowerby II")), WikidataPersonSource.IdProperty.P835, persons);
    WikidataPersonSource.addIds(rows(id("Q2", "abc-def")), WikidataPersonSource.IdProperty.P2006, persons);
    var source = new WikidataPersonSource(url -> {
      throw new AssertionError("no network");
    });
    var pending = new WikidataPersonSource.Pending();
    String claims = claims(
      time("P569", "+1812-08-12T00:00:00Z"),
      time("P570", "1884-07-26T00:00:00Z"),
      // a deprecated statement is wrong by definition
      claim("P570", "{\"time\":\"+1799-01-01T00:00:00Z\"}", "deprecated"),
      item("P734", "Q100"),
      item("P735", "Q101"),
      item("P735", "Q102"),
      item("P101", "Q200"),
      item("P101", "Q201"),
      item("P22", "Q1"),
      item("P3373", "Q3"),
      // an unknown value has no datavalue
      "{\"p\":\"P25\",\"mainsnak\":{\"snaktype\":\"somevalue\",\"property\":\"P25\"},\"rank\":\"normal\"}"
    );
    WikidataPersonSource.addEntities(MAPPER.readTree(entities(entityWithClaims("Q2", claims, "George Brettingham Sowerby II", "G. B. Sowerby"))),
      persons, pending);
    assertEquals(Set.of("Q100", "Q101", "Q102", "Q200", "Q201"), pending.items());
    source.resolve(persons, pending, WikidataPersonSource.labels(MAPPER.readTree(entities(entity("Q100", "Sowerby"),
      entity("Q101", "Brettingham"), entity("Q102", "George"), entity("Q200", "malacology"), entity("Q201", "politics")))));

    PersonRecord r = persons.get("Q2").build();
    assertEquals("Q2", r.wikidata());
    assertEquals("9936-1", r.ipni());
    assertEquals("ABC-DEF", r.zoobank());
    assertEquals("Sowerby", r.family());
    assertEquals("George Brettingham", r.given());
    assertEquals("II", r.suffix());
    assertEquals(Integer.valueOf(1812), r.born());
    assertEquals(Integer.valueOf(1884), r.died());
    assertEquals(Set.of(TaxGroup.Molluscs), r.groups());
    assertTrue(r.names().contains(new PersonRecord.Form("G.B.Sowerby II", NameKind.CITATION, FormCode.ZOO)));
    assertTrue(r.names().contains(new PersonRecord.Form("George Brettingham Sowerby II", NameKind.FULL, FormCode.ANY)));
    assertTrue(r.names().contains(new PersonRecord.Form("G. B. Sowerby", NameKind.VARIANT, FormCode.ANY)));
    assertEquals(List.of(new PersonRecord.Link(RelationType.PARENT, "wd:Q1"), new PersonRecord.Link(RelationType.SIBLING, "wd:Q3")),
      r.relations());
    assertTrue(source.stats(), source.stats().contains("politics"));
  }

  /** the query service answers HTTP 431 to a request line above 8 KB, a batch of redirects must stay below */
  @Test
  public void redirectBatchFitsIntoAGetRequest() {
    List<String> qids = IntStream.range(0, WikidataPersonSource.BATCH).mapToObj(i -> "Q" + (123456789 + i)).toList();
    String url = WikidataPersonSource.SPARQL + "?format=json&query="
      + URLEncoder.encode(WikidataPersonSource.redirectQuery(qids), StandardCharsets.UTF_8);
    assertTrue(url.length() + " characters", url.length() < 6000);
  }

  @Test
  public void standardFormIsBotanical() throws Exception {
    Map<String, PersonRecord.Builder> persons = new TreeMap<>();
    WikidataPersonSource.addIds(rows(id("Q5", "Sw.")), WikidataPersonSource.IdProperty.P428, persons);
    assertEquals(List.of(new PersonRecord.Form("Sw.", NameKind.STANDARD, FormCode.BOT)), persons.get("Q5").build().names());
  }

  /** read() pages the ids through the query service, then asks the API for the persons and their name items */
  @Test
  public void read() throws Exception {
    var source = new WikidataPersonSource(url -> {
      String q = URLDecoder.decode(url, StandardCharsets.UTF_8);
      if (q.contains("wbgetentities") && q.contains("ids=Q9")) return entities(entityWithClaims("Q9", claims(item("P734", "Q77")), "Olof Swartz"));
      if (q.contains("wbgetentities") && q.contains("ids=Q77")) return entities(entity("Q77", "Swartz"));
      if (q.contains("wdt:P428") && q.contains("OFFSET 0")) return sparql(id("Q9", "Sw."));
      return sparql();
    });
    List<PersonRecord> records = source.read();
    assertEquals(1, records.size());
    assertEquals("Q9", records.get(0).wikidata());
    assertEquals("Swartz", records.get(0).family());
    assertEquals(2, records.get(0).names().size());
  }

  /** a label only in "mul", the language Wikidata keeps names in for all languages, is asked for again */
  @Test
  public void mulLabels() throws Exception {
    var source = new WikidataPersonSource(url -> {
      String q = URLDecoder.decode(url, StandardCharsets.UTF_8);
      if (q.contains("wbgetentities") && q.contains("languages=mul") && q.contains("ids=Q9"))
        return "{\"entities\":{\"Q9\":{\"id\":\"Q9\",\"labels\":{\"mul\":{\"language\":\"mul\",\"value\":\"Kurt M. Neubig\"}}}}}";
      if (q.contains("wbgetentities") && q.contains("languages=mul") && q.contains("ids=Q77"))
        return "{\"entities\":{\"Q77\":{\"id\":\"Q77\",\"labels\":{\"mul\":{\"language\":\"mul\",\"value\":\"Neubig\"}}}}}";
      if (q.contains("wbgetentities") && q.contains("ids=Q9")) return entities(entityWithClaims("Q9", claims(item("P734", "Q77")), null));
      if (q.contains("wbgetentities") && q.contains("ids=Q77")) return entities(entity("Q77", null));
      if (q.contains("wdt:P428") && q.contains("OFFSET 0")) return sparql(id("Q9", "Neubig"));
      return sparql();
    });
    PersonRecord r = source.read().get(0);
    assertTrue(r.names().contains(new PersonRecord.Form("Kurt M. Neubig", NameKind.FULL, FormCode.ANY)));
    assertEquals("Neubig", r.family());
  }

  @Test
  public void redirects() throws Exception {
    var source = new WikidataPersonSource(url -> "{\"results\":{\"bindings\":[{\"old\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/Q1\"},"
      + "\"new\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/Q2\"}}]}}");
    assertEquals(Map.of("Q1", "Q2"), source.redirects(List.of("Q1")));
    assertEquals(Map.of(), source.redirects(List.of()));
  }

  /** an item with two IPNI or ZooBank ids: the first is the person's, the others are the same person recorded twice */
  @Test
  public void severalIdsOfOneAuthority() throws Exception {
    Map<String, PersonRecord.Builder> persons = new TreeMap<>();
    WikidataPersonSource.addIds(rows(id("Q2", "1-1"), id("Q2", "2-2")), WikidataPersonSource.IdProperty.P586, persons);
    WikidataPersonSource.addIds(rows(id("Q2", "abc"), id("Q2", "def")), WikidataPersonSource.IdProperty.P2006, persons);
    persons.get("Q2").label("Anna Smith");
    PersonRecord r = persons.get("Q2").build();
    assertEquals("1-1", r.ipni());
    assertEquals("ABC", r.zoobank());
    assertEquals(Set.of("ipni:2-2", "zb:DEF"), r.otherIds());
  }
}
