package life.catalogue.resources.matching.openrefine;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.UsageMatch;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

public class OpenRefineMapperTest {

  static SimpleNameClassified<SimpleNameCached> name(String id, String name, String authorship, MatchType nidxType) {
    var sn = SimpleNameClassified.snc(id, Rank.SPECIES, NomCode.ZOOLOGICAL, TaxonomicStatus.ACCEPTED, name, authorship);
    sn.setNamesIndexMatchType(nidxType);
    return sn;
  }

  @Test
  public void scoreOrdering() {
    assertEquals(100.0, OpenRefineMapper.score(MatchType.EXACT), 0.0001);
    assertEquals(0.0, OpenRefineMapper.score(MatchType.NONE), 0.0001);
    assertEquals(0.0, OpenRefineMapper.score(null), 0.0001);
    assertTrue(OpenRefineMapper.score(MatchType.EXACT) > OpenRefineMapper.score(MatchType.VARIANT));
    assertTrue(OpenRefineMapper.score(MatchType.VARIANT) > OpenRefineMapper.score(MatchType.CANONICAL));
    assertTrue(OpenRefineMapper.score(MatchType.CANONICAL) > OpenRefineMapper.score(MatchType.AMBIGUOUS));
    assertTrue(OpenRefineMapper.score(MatchType.AMBIGUOUS) > OpenRefineMapper.score(MatchType.HIGHERRANK));
    assertTrue(OpenRefineMapper.score(MatchType.HIGHERRANK) > 0);
  }

  @Test
  public void exactMatchIsAutoMatched() {
    var usage = name("42", "Puma concolor", "(Linnaeus, 1771)", MatchType.EXACT);
    var match = UsageMatch.match(MatchType.EXACT, usage, 3, null);

    var result = OpenRefineMapper.toResult(match);

    assertEquals(1, result.result.size());
    var c = result.result.get(0);
    assertEquals("42", c.id);
    assertEquals("Puma concolor (Linnaeus, 1771)", c.name);
    assertEquals(100.0, c.score, 0.0001);
    assertTrue(c.match);
    assertEquals(1, c.type.size());
    assertEquals("Taxon", c.type.get(0).id);
  }

  @Test
  public void ambiguousIsNotAutoMatched() {
    var usage = name("1", "Aus", null, MatchType.AMBIGUOUS);
    var alt = name("2", "Aus", null, MatchType.AMBIGUOUS);
    var match = UsageMatch.match(MatchType.AMBIGUOUS, usage, 3, List.of(alt));

    var result = OpenRefineMapper.toResult(match);

    assertEquals(2, result.result.size());
    assertFalse("ambiguous primary must not auto-match", result.result.get(0).match);
    assertFalse(result.result.get(1).match);
  }

  @Test
  public void noMatchYieldsEmptyResult() {
    var result = OpenRefineMapper.toResult(UsageMatch.empty(3));
    assertTrue(result.result.isEmpty());
  }

  @Test
  public void manifestHasDatasetScopedUrls() {
    var m = OpenRefineMapper.manifest(3, "https://api.checklistbank.org/dataset/3/reconcile", "https://www.checklistbank.org");

    assertTrue(m.versions.contains("0.2"));
    assertTrue(m.name.contains("Catalogue of Life"));
    assertTrue(m.identifierSpace.contains("/dataset/3/taxon/"));
    assertTrue(m.view.url.contains("/dataset/3/taxon/"));
    assertTrue(m.view.url.contains("{{id}}"));
    assertEquals("Taxon", m.defaultTypes.get(0).id);
    assertNotNull(m.suggest.entity);
    assertNotNull(m.extend);
  }

  @Test
  public void extendValuesFromUsageAndClassification() {
    var usage = name("42", "Puma concolor", "(Linnaeus, 1771)", MatchType.EXACT);
    usage.setNamesIndexId(987);
    var family = SimpleNameClassified.snc("f1", Rank.FAMILY, NomCode.ZOOLOGICAL, TaxonomicStatus.ACCEPTED, "Felidae", null);
    usage.setClassification(List.of(family));

    assertEquals("Puma concolor", OpenRefineMapper.extendValue(usage, "scientificName"));
    assertEquals("(Linnaeus, 1771)", OpenRefineMapper.extendValue(usage, "authorship"));
    assertEquals("species", OpenRefineMapper.extendValue(usage, "rank"));
    assertEquals("987", OpenRefineMapper.extendValue(usage, "nidx"));
    assertEquals("Felidae", OpenRefineMapper.extendValue(usage, "family"));
    assertNull(OpenRefineMapper.extendValue(usage, "kingdom"));
    assertNull(OpenRefineMapper.extendValue(usage, "bogus"));
  }

  @Test
  public void buildExtendResponseFillsRequestedColumns() {
    var usage = name("42", "Puma concolor", "(Linnaeus, 1771)", MatchType.EXACT);
    var family = SimpleNameClassified.snc("f1", Rank.FAMILY, NomCode.ZOOLOGICAL, TaxonomicStatus.ACCEPTED, "Felidae", null);
    usage.setClassification(List.of(family));

    var resp = OpenRefineMapper.buildExtendResponse(
      List.of("42", "missing"),
      List.of("authorship", "family"),
      Map.of("42", usage));

    // meta carries human readable names from the property catalog
    assertEquals(2, resp.meta.size());
    assertEquals("authorship", resp.meta.get(0).id);

    // matched id has the values
    assertEquals("(Linnaeus, 1771)", resp.rows.get("42").get("authorship").get(0).str);
    assertEquals("Felidae", resp.rows.get("42").get("family").get(0).str);

    // unknown id is still present with empty cells
    assertTrue(resp.rows.containsKey("missing"));
    assertTrue(resp.rows.get("missing").get("authorship").isEmpty());
  }

  @Test
  public void toSuggestResponseMapsEntities() {
    var s = new life.catalogue.api.search.NameUsageSuggestion();
    s.setUsageId("u1");
    s.setMatch("Puma concolor");
    s.setRank(Rank.SPECIES);
    s.setStatus(TaxonomicStatus.ACCEPTED);

    var resp = OpenRefineMapper.toSuggestResponse(List.of(s));

    assertEquals(1, resp.result.size());
    assertEquals("u1", resp.result.get(0).id);
    assertEquals("Puma concolor", resp.result.get(0).name);
    assertEquals("Taxon", resp.result.get(0).type.get(0).id);
  }

  @Test
  public void parseQueriesParsesBatch() throws Exception {
    String json = "{\"q0\":{\"query\":\"Puma concolor\"},\"q1\":{\"query\":\"Aus bus\",\"limit\":3}}";
    Map<String, OpenRefineModel.Query> queries = OpenRefineMapper.parseQueries(json, ApiModule.MAPPER);

    assertEquals(2, queries.size());
    assertEquals("Puma concolor", queries.get("q0").query);
    assertEquals(Integer.valueOf(3), queries.get("q1").limit);
  }
}
