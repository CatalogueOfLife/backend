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
}
