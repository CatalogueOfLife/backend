package life.catalogue.resources.parser.openrefine;

import life.catalogue.parser.RankParser;
import life.catalogue.resources.matching.openrefine.OpenRefineModel;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.*;

public class ParserOpenRefineMapperTest {

  @Test
  public void vocabResultAutoMatchesEnum() {
    OpenRefineModel.Result r = ParserOpenRefineMapper.vocabResult(RankParser.PARSER, "rank", "sp.");
    assertEquals(1, r.result.size());
    var c = r.result.get(0);
    assertEquals(Rank.SPECIES.name(), c.id); // "SPECIES"
    assertTrue(c.match);
    assertEquals(100.0, c.score, 0.0001);
  }

  @Test
  public void vocabResultEmptyWhenUnparsable() {
    OpenRefineModel.Result r = ParserOpenRefineMapper.vocabResult(RankParser.PARSER, "rank", "@@@nonsense@@@");
    assertTrue(r.result.isEmpty());
  }

  @Test
  public void enumSuggestFiltersByPrefix() {
    var resp = ParserOpenRefineMapper.enumSuggest(Rank.class, "spec", 25);
    assertTrue(resp.result.stream().anyMatch(i -> i.id.equals(Rank.SPECIES.name())));
    assertTrue(resp.result.stream().noneMatch(i -> i.id.equals(Rank.GENUS.name())));
  }

  @Test
  public void vocabManifestOmitsSuggestWhenNotEnum() {
    var m = ParserOpenRefineMapper.vocabManifest("uri", "http://x/parser/uri/reconcile", "http://clb", false);
    assertNull(m.suggest);
    var m2 = ParserOpenRefineMapper.vocabManifest("rank", "http://x/parser/rank/reconcile", "http://clb", true);
    assertNotNull(m2.suggest);
    assertNotNull(m2.suggest.entity);
  }

  @Test
  public void nameExtendValuesWithCode() throws Exception {
    var pnu = life.catalogue.parser.NameParser.PARSER
      .parse("Abies alba", "Mill.", org.gbif.nameparser.api.Rank.SPECIES,
             org.gbif.nameparser.api.NomCode.BOTANICAL, life.catalogue.api.model.IssueContainer.VOID)
      .get();
    var n = pnu.getName();
    assertEquals("alba", ParserOpenRefineMapper.nameValue(n, pnu, "specificEpithet"));
    assertEquals("Abies", ParserOpenRefineMapper.nameValue(n, pnu, "genus"));
    assertEquals("species", ParserOpenRefineMapper.nameValue(n, pnu, "rank"));
    assertNotNull(ParserOpenRefineMapper.nameValue(n, pnu, "label"));
    assertNotNull(ParserOpenRefineMapper.nameValue(n, pnu, "labelHtml"));
    assertEquals("true", ParserOpenRefineMapper.nameValue(n, pnu, "parsed"));
  }

  @Test
  public void nameManifestHasCodeAndRankSettings() {
    var m = ParserOpenRefineMapper.nameManifest("http://x/parser/name/reconcile", "http://clb");
    assertNull(m.suggest);
    assertNotNull(m.extend);
    assertNotNull(m.extend.property_settings);
    assertTrue(m.extend.property_settings.stream().anyMatch(s -> s.name.equals("code")));
    assertTrue(m.extend.property_settings.stream().anyMatch(s -> s.name.equals("rank")));
  }

  @Test
  public void geoTimeExtendValues() {
    var gt = life.catalogue.api.vocab.GeoTime.byName("Holocene");
    assertNotNull(gt);
    assertEquals(gt.getName(), ParserOpenRefineMapper.geoTimeValue(gt, "name"));
    assertNotNull(ParserOpenRefineMapper.geoTimeValue(gt, "type"));
    assertNotNull(ParserOpenRefineMapper.geoTimeValue(gt, "end"));
    assertNotNull(ParserOpenRefineMapper.geoTimeValue(gt, "start"));
  }

  @Test
  public void geoTimeSuggestByPrefix() {
    var resp = ParserOpenRefineMapper.geoTimeSuggest("holo", 25);
    assertTrue(resp.result.stream().anyMatch(i -> i.name.toLowerCase().startsWith("holo")));
  }

  @Test
  public void taxGroupExtendValues() {
    var g = life.catalogue.api.vocab.TaxGroup.Viruses;
    assertNotNull(ParserOpenRefineMapper.taxGroupValue(g, "codes")); // NomCode.VIRUS
  }

  @Test
  public void taxGroupSuggestByPrefix() {
    var resp = ParserOpenRefineMapper.taxGroupSuggest("vir", 25);
    assertTrue(resp.result.stream().anyMatch(i -> i.id.equals(life.catalogue.api.vocab.TaxGroup.Viruses.name())));
  }

  @Test
  public void areaResultUsesGlobalId() {
    var r = ParserOpenRefineMapper.areaResult("tdwg:14");
    assertEquals(1, r.result.size());
    assertEquals("tdwg:14", r.result.get(0).id);
    assertTrue(r.result.get(0).match);
  }

  @Test
  public void areaFreeTextHasNoCandidate() {
    var r = ParserOpenRefineMapper.areaResult("Germany"); // TEXT area, no globalId
    assertTrue(r.result.isEmpty());
  }

  @Test
  public void areaExtendValues() throws Exception {
    var a = life.catalogue.parser.AreaParser.PARSER.parse("tdwg:14").orElse(null);
    assertNotNull(a);
    assertEquals("tdwg:14", ParserOpenRefineMapper.areaValue(a, "globalId"));
    assertNotNull(ParserOpenRefineMapper.areaValue(a, "gazetteer"));
  }
}
