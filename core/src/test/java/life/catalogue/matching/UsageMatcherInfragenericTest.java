package life.catalogue.matching;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.MatchingMode;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.interpreter.NameInterpreter;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NamesIndexConfig;

import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * A genus followed by an infrageneric epithet in brackets is a name below that genus. The names index only knows the
 * infrageneric epithet, so it shares its canonical with the genus of that name, a homonym elsewhere in the tree:
 * Chaetoceros (Hyalochaete), a diatom subgenus, against the plant genus Hyalochaete in release 315557 (COL26.6 XR).
 */
public class UsageMatcherInfragenericTest {
  final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);
  NameIndex ni;
  MatchingUtils utils;
  UsageMatcherMemStore store;
  UsageMatcher matcher;

  @Before
  public void init() throws Exception {
    ni = NameIndexFactory.build(NamesIndexConfig.memory(512), null, AuthorshipNormalizer.createWithoutAuthormap()).started();
    utils = new MatchingUtils(ni);
    store = new UsageMatcherMemStore(1);
    add("pl", "Plantae", Rank.KINGDOM, null, null, TaxonomicStatus.ACCEPTED);
    add("jur", "Jurinea", Rank.GENUS, "Cass.", "pl", TaxonomicStatus.ACCEPTED);
    add("hya", "Hyalochaete", Rank.GENUS, "Dittrich & Rech.fil.", "jur", TaxonomicStatus.SYNONYM);
    add("chr", "Chromista", Rank.KINGDOM, null, null, TaxonomicStatus.ACCEPTED);
    add("cha", "Chaetoceros", Rank.GENUS, "Ehrenberg", "chr", TaxonomicStatus.ACCEPTED);
    add("an", "Animalia", Rank.KINGDOM, null, null, TaxonomicStatus.ACCEPTED);
    add("aedes", "Aedes", Rank.GENUS, "Meigen, 1818", "an", TaxonomicStatus.ACCEPTED);
    add("steg", "Aedes (Stegomyia)", Rank.SUBGENUS, "Theobald, 1901", "aedes", TaxonomicStatus.ACCEPTED);
    matcher = new UsageMatcher(1, ni, store, false);
  }

  @After
  public void close() throws Exception {
    ni.close();
  }

  void add(String id, String name, Rank rank, String authorship, String parent, TaxonomicStatus status) {
    var sn = new SimpleNameCached(id, name, rank);
    sn.setAuthorship(authorship);
    sn.setParent(parent);
    sn.setStatus(status);
    sn.applyMatch(ni.match(sn, true, false));
    store.add(sn);
  }

  QueryName query(String name) {
    return AbstractMatchingJob.interpret(SimpleName.sn(name), List.of(), new IssueContainer.Simple(), interpreter, utils).get();
  }

  UsageMatch match(String name, MatchingMode mode) throws Exception {
    return matcher.match(query(name), mode, false);
  }

  @Test
  public void notTheGenusOfTheEpithet() throws Exception {
    assertEquals(store.get("hya").getNamesIndexId(), query("Chaetoceros (Hyalochaete) spp.").getNamesIndexId());

    for (String name : List.of("Chaetoceros (Hyalochaete) spp.", "Chaetoceros (Hyalochaete)")) {
      var m = match(name, MatchingMode.RELAXED);
      assertEquals(name, MatchType.HIGHERRANK, m.type);
      assertEquals(name, "cha", m.usage.getId());
      assertFalse(name, match(name, MatchingMode.STRICT).isMatch());
    }
    // the genus itself
    assertEquals("hya", match("Hyalochaete", MatchingMode.STRICT).usage.getId());
  }

  @Test
  public void subgenus() throws Exception {
    var m = match("Aedes (Stegomyia) Theobald, 1901", MatchingMode.STRICT);
    assertEquals(MatchType.EXACT, m.type);
    assertEquals("steg", m.usage.getId());
    m = match("Aedes (Stegomyia)", MatchingMode.STRICT);
    assertEquals(MatchType.VARIANT, m.type);
    assertEquals("steg", m.usage.getId());
  }
}
