package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.person.FormCode;
import life.catalogue.matching.person.NameKind;
import life.catalogue.matching.person.RelationType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;

public class WikidataPersonSourceTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static JsonNode rows(String... rows) throws Exception {
    return MAPPER.readTree("{\"results\":{\"bindings\":[" + String.join(",", rows) + "]}}");
  }

  static String id(String q, String v) {
    return "{\"person\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/" + q + "\"},\"v\":{\"type\":\"literal\",\"value\":\"" + v + "\"}}";
  }

  static String fact(String q, String p, String v) {
    return "{\"person\":{\"type\":\"uri\",\"value\":\"http://www.wikidata.org/entity/" + q + "\"},\"p\":{\"type\":\"literal\",\"value\":\""
      + p + "\"},\"v\":{\"type\":\"literal\",\"value\":\"" + v + "\"}}";
  }

  @Test
  public void idsAndFacts() throws Exception {
    Map<String, PersonRecord.Builder> persons = new TreeMap<>();
    assertEquals(1, WikidataPersonSource.addIds(rows(id("Q2", "9936-1")), WikidataPersonSource.IdProperty.P586, persons));
    WikidataPersonSource.addIds(rows(id("Q2", "G.B.Sowerby II")), WikidataPersonSource.IdProperty.P835, persons);
    WikidataPersonSource.addIds(rows(id("Q2", "abc-def")), WikidataPersonSource.IdProperty.P2006, persons);
    var source = new WikidataPersonSource(url -> {
      throw new AssertionError("no network");
    });
    source.addFacts(rows(
      fact("Q2", "label", "George Brettingham Sowerby II"),
      fact("Q2", "alias", "G. B. Sowerby"),
      fact("Q2", "family", "Sowerby"),
      fact("Q2", "given", "Brettingham"),
      fact("Q2", "given", "George"),
      fact("Q2", "born", "+1812-08-12T00:00:00Z"),
      fact("Q2", "died", "1884-07-26T00:00:00Z"),
      fact("Q2", "field", "malacology"),
      fact("Q2", "field", "politics"),
      fact("Q2", "parent", "http://www.wikidata.org/entity/Q1"),
      fact("Q2", "sibling", "http://www.wikidata.org/entity/Q3")
    ), persons);
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

  /** the query service answers HTTP 431 to a request line above 8 KB, a batch of facts must stay below */
  @Test
  public void factBatchFitsIntoAGetRequest() {
    List<String> qids = java.util.stream.IntStream.range(0, WikidataPersonSource.BATCH).mapToObj(i -> "Q" + (123456789 + i)).toList();
    String url = WikidataPersonSource.ENDPOINT + "?format=json&query="
      + java.net.URLEncoder.encode(WikidataPersonSource.factQuery(qids), java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(url.length() + " characters", url.length() < 6000);
  }

  @Test
  public void standardFormIsBotanical() throws Exception {
    Map<String, PersonRecord.Builder> persons = new TreeMap<>();
    WikidataPersonSource.addIds(rows(id("Q5", "Sw.")), WikidataPersonSource.IdProperty.P428, persons);
    assertEquals(List.of(new PersonRecord.Form("Sw.", NameKind.STANDARD, FormCode.BOT)), persons.get("Q5").build().names());
  }

  /** read() pages the id queries and batches the facts, all through the fetcher */
  @Test
  public void read() throws Exception {
    var source = new WikidataPersonSource(url -> {
      String q = java.net.URLDecoder.decode(url, java.nio.charset.StandardCharsets.UTF_8);
      if (q.contains("VALUES ?person")) return "{\"results\":{\"bindings\":[" + fact("Q9", "label", "Olof Swartz") + "]}}";
      if (q.contains("wdt:P428") && q.contains("OFFSET 0")) return "{\"results\":{\"bindings\":[" + id("Q9", "Sw.") + "]}}";
      return "{\"results\":{\"bindings\":[]}}";
    });
    List<PersonRecord> records = source.read();
    assertEquals(1, records.size());
    assertEquals("Q9", records.get(0).wikidata());
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
