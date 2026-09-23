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

  static String statement(String q, String p, String v) {
    return "{\"person\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/" + q + "\"},"
      + "\"prop\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/prop/direct/" + p + "\"},\"v\":{\"value\":\"" + v + "\"}}";
  }

  static String entities(String... entities) {
    return "{\"entities\":{" + String.join(",", entities) + "}}";
  }

  static String entity(String q, String label, String... aliases) {
    StringBuilder sb = new StringBuilder("\"" + q + "\":{\"id\":\"" + q + "\"");
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

  static final String ITEM = "http://www.wikidata.org/entity/";

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
    WikidataPersonSource.addStatements(rows(
      statement("Q2", "P569", "+1812-08-12T00:00:00Z"),
      statement("Q2", "P570", "1884-07-26T00:00:00Z"),
      statement("Q2", "P734", ITEM + "Q100"),
      statement("Q2", "P735", ITEM + "Q101"),
      statement("Q2", "P735", ITEM + "Q102"),
      statement("Q2", "P101", ITEM + "Q200"),
      statement("Q2", "P101", ITEM + "Q201"),
      statement("Q2", "P22", ITEM + "Q1"),
      statement("Q2", "P3373", ITEM + "Q3")
    ), persons, pending);
    WikidataPersonSource.addEntities(MAPPER.readTree(entities(entity("Q2", "George Brettingham Sowerby II", "G. B. Sowerby"))), persons);
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

  /** the query service answers HTTP 431 to a request line above 8 KB, a batch of statements must stay below */
  @Test
  public void statementBatchFitsIntoAGetRequest() {
    List<String> qids = IntStream.range(0, WikidataPersonSource.BATCH).mapToObj(i -> "Q" + (123456789 + i)).toList();
    String url = WikidataPersonSource.SPARQL + "?format=json&query="
      + URLEncoder.encode(WikidataPersonSource.statementQuery(qids), StandardCharsets.UTF_8);
    assertTrue(url.length() + " characters", url.length() < 6000);
  }

  @Test
  public void standardFormIsBotanical() throws Exception {
    Map<String, PersonRecord.Builder> persons = new TreeMap<>();
    WikidataPersonSource.addIds(rows(id("Q5", "Sw.")), WikidataPersonSource.IdProperty.P428, persons);
    assertEquals(List.of(new PersonRecord.Form("Sw.", NameKind.STANDARD, FormCode.BOT)), persons.get("Q5").build().names());
  }

  /** read() pages the ids, batches the statements through SPARQL and the labels through the API */
  @Test
  public void read() throws Exception {
    var source = new WikidataPersonSource(url -> {
      String q = URLDecoder.decode(url, StandardCharsets.UTF_8);
      if (q.contains("wbgetentities") && q.contains("ids=Q9")) return entities(entity("Q9", "Olof Swartz"));
      if (q.contains("wbgetentities") && q.contains("ids=Q77")) return entities(entity("Q77", "Swartz"));
      if (q.contains("VALUES ?prop")) return sparql(statement("Q9", "P734", ITEM + "Q77"));
      if (q.contains("wdt:P428") && q.contains("OFFSET 0")) return sparql(id("Q9", "Sw."));
      return sparql();
    });
    List<PersonRecord> records = source.read();
    assertEquals(1, records.size());
    assertEquals("Q9", records.get(0).wikidata());
    assertEquals("Swartz", records.get(0).family());
    assertEquals(2, records.get(0).names().size());
  }

  @Test
  public void redirects() throws Exception {
    var source = new WikidataPersonSource(url -> "{\"results\":{\"bindings\":[{\"old\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/Q1\"},"
      + "\"new\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/Q2\"}}]}}");
    assertEquals(Map.of("Q1", "Q2"), source.redirects(List.of("Q1")));
    assertEquals(Map.of(), source.redirects(List.of()));
  }
}
