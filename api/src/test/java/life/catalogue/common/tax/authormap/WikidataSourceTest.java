package life.catalogue.common.tax.authormap;

import com.fasterxml.jackson.databind.*;
import life.catalogue.common.io.Resources;
import java.io.InputStream;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WikidataSourceTest {
  @Test
  public void parsesSparqlBindings() throws Exception {
    ObjectMapper om = new ObjectMapper();
    JsonNode json;
    try (InputStream in = Resources.stream("authormap/wikidata-sample.json")) {
      json = om.readTree(in);
    }
    List<AuthorEntry> entries = new WikidataSource().parse(json);
    // botanist: recorded as BOT via P428, but the abbreviation itself is NOT imported (only the full name)
    AuthorEntry linn = entries.stream().filter(e -> e.aliases().contains("Carl Linnaeus")).findFirst().orElseThrow();
    assertEquals(AuthorCode.BOT, linn.code());               // has botanist abbrev (P428)
    assertFalse(linn.aliases().contains("L."));              // botanical abbreviation deliberately dropped
    // zoologist: the P835 citation IS imported (that is the form used in zoological names)
    AuthorEntry cuv = entries.stream().filter(e -> e.aliases().contains("Georges Cuvier")).findFirst().orElseThrow();
    assertEquals(AuthorCode.ZOO, cuv.code());                // has zoologist citation (P835)
    assertTrue(cuv.aliases().contains("Cuvier"));            // zoological citation kept
  }

  @Test
  public void dropsBotanicalAbbrevAndSuffixedZooCitations() throws Exception {
    String body = """
      { "results": { "bindings": [
        { "person": {"value":"http://www.wikidata.org/entity/Q1"},
          "name": {"value":"Fred Reuel Jones"}, "botAbbr": {"value":"F.R.Jones"} },
        { "person": {"value":"http://www.wikidata.org/entity/Q2"},
          "name": {"value":"Andrew Smith"}, "zooAuthor": {"value":"A. Smith bis"} },
        { "person": {"value":"http://www.wikidata.org/entity/Q3"},
          "name": {"value":"Georges Cuvier"}, "zooAuthor": {"value":"Cuvier"} }
      ] } }""";
    JsonNode json = new ObjectMapper().readTree(body);
    List<AuthorEntry> entries = new WikidataSource().parse(json);

    // botanist: abbreviation not imported at all, only the full name; still coded BOT
    AuthorEntry jones = entries.stream().filter(e -> e.canonical().equals("Fred Reuel Jones")).findFirst().orElseThrow();
    assertTrue(jones.aliases().contains("Fred Reuel Jones"));
    assertFalse(jones.aliases().contains("F.R.Jones"));                  // botanical abbreviation dropped
    assertEquals(AuthorCode.BOT, jones.code());
    // zoologist with a suffixed citation: the suffixed form is skipped, label kept
    AuthorEntry smith = entries.stream().filter(e -> e.canonical().equals("Andrew Smith")).findFirst().orElseThrow();
    assertFalse(smith.aliases().stream().anyMatch(a -> a.equalsIgnoreCase("A. Smith bis")));
    assertEquals(AuthorCode.ZOO, smith.code());
    // zoologist with a normal citation: kept
    AuthorEntry cuv = entries.stream().filter(e -> e.canonical().equals("Georges Cuvier")).findFirst().orElseThrow();
    assertTrue(cuv.aliases().contains("Cuvier"));
  }

  @Test
  public void accumulatesAcrossPagesAndProperties() throws Exception {
    ObjectMapper om = new ObjectMapper();
    // page from the P428 (botanist) query
    JsonNode botPage = om.readTree("""
      { "results": { "bindings": [
        { "person": {"value":"http://www.wikidata.org/entity/Q1"}, "name": {"value":"Carl Linnaeus"}, "botAbbr": {"value":"L."} }
      ] } }""");
    // page from the P835 (zoologist) query for the SAME person -> must promote to ANY and union aliases
    JsonNode zooPage = om.readTree("""
      { "results": { "bindings": [
        { "person": {"value":"http://www.wikidata.org/entity/Q1"}, "name": {"value":"Carl Linnaeus"}, "zooAuthor": {"value":"Linnaeus"} }
      ] } }""");

    WikidataSource.Ctx ctx = new WikidataSource.Ctx();
    assertEquals(1, ctx.accumulate(botPage));   // returns the page row count for pagination termination
    assertEquals(1, ctx.accumulate(zooPage));
    List<AuthorEntry> entries = ctx.build();

    assertEquals(1, entries.size());             // one person across both pages
    AuthorEntry e = entries.get(0);
    assertEquals(AuthorCode.ANY, e.code());      // P428 + P835 -> ANY
    assertTrue(e.aliases().containsAll(List.of("Carl Linnaeus", "Linnaeus"))); // label + zoo citation
    assertFalse(e.aliases().contains("L."));     // botanical abbreviation not imported
  }
}
