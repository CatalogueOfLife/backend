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
    // one row per author, abbreviation + label aliases, code from which property matched
    AuthorEntry linn = entries.stream().filter(e -> e.aliases().contains("Carl Linnaeus")).findFirst().orElseThrow();
    assertEquals(AuthorCode.BOT, linn.code());               // has botanist abbrev (P428)
    assertTrue(linn.aliases().contains("L."));
    AuthorEntry cuv = entries.stream().filter(e -> e.aliases().contains("Georges Cuvier")).findFirst().orElseThrow();
    assertEquals(AuthorCode.ZOO, cuv.code());                // has zoologist citation (P835)
  }

  @Test
  public void dropsSuffixedAbbreviations() throws Exception {
    // Wikidata bundles a nomenclatural suffix into some abbreviations ("F.R.Jones bis", "A.M.Sm.bis").
    // These must not become lookup keys or they'd erase the bis/ter distinction. The author + label survive.
    String body = """
      { "results": { "bindings": [
        { "person": {"value":"http://www.wikidata.org/entity/Q1"},
          "name": {"value":"Fred Reuel Jones"}, "botAbbr": {"value":"F.R.Jones bis"} },
        { "person": {"value":"http://www.wikidata.org/entity/Q2"},
          "name": {"value":"Alan Smith"}, "botAbbr": {"value":"A.M.Sm.bis"} }
      ] } }""";
    JsonNode json = new ObjectMapper().readTree(body);
    List<AuthorEntry> entries = new WikidataSource().parse(json);

    AuthorEntry jones = entries.stream().filter(e -> e.canonical().equals("Fred Reuel Jones")).findFirst().orElseThrow();
    assertTrue(jones.aliases().contains("Fred Reuel Jones"));            // label kept
    assertFalse(jones.aliases().contains("F.R.Jones bis"));              // suffixed abbrev dropped
    assertEquals(AuthorCode.BOT, jones.code());                         // still recorded as a botanist
    AuthorEntry smith = entries.stream().filter(e -> e.canonical().equals("Alan Smith")).findFirst().orElseThrow();
    assertFalse(smith.aliases().contains("A.M.Sm.bis"));                // dot-attached suffix dropped too
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
    assertTrue(e.aliases().containsAll(List.of("Carl Linnaeus", "L.", "Linnaeus")));
  }
}
