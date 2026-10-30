package life.catalogue.matching;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.vocab.Issue;
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
 * External match queries must never insert into the names index, which is only built from names held in ChecklistBank.
 */
public class MatchingInterpretTest {
  NameIndex ni;
  MatchingUtils utils;
  final NameInterpreter interpreter = new NameInterpreter(new DatasetSettings(), true);

  @Before
  public void init() throws Exception {
    ni = NameIndexFactory.build(NamesIndexConfig.memory(512), null, AuthorshipNormalizer.createWithoutAuthormap()).started();
    utils = new MatchingUtils(ni);
  }

  @After
  public void close() throws Exception {
    ni.close();
  }

  @Test
  public void interpretNeverInserts() {
    var snc = AbstractMatchingJob.interpret(SimpleName.sn("Abies alba Mill."), List.of(), new IssueContainer.Simple(), interpreter, utils);
    assertTrue(snc.isPresent());
    assertNull(snc.get().getNamesIndexId());
    assertEquals(0, ni.size());
  }

  @Test
  public void rankContradictsName() {
    assertTrue(AbstractMatchingJob.rankContradictsName("Oryctolagus cuniculus cuniculus", Rank.SPECIES));
    assertFalse(AbstractMatchingJob.rankContradictsName("Oryctolagus cuniculus cuniculus", Rank.SUBSPECIES));
    assertTrue(AbstractMatchingJob.rankContradictsName("Macropus Shaw, 1790", Rank.SPECIES));
    assertFalse(AbstractMatchingJob.rankContradictsName("Macropus Shaw, 1790", Rank.GENUS));
    assertFalse(AbstractMatchingJob.rankContradictsName("Pipistrellus kuhlii (Natterer in Kuhl, 1817)", Rank.SPECIES));
    assertTrue(AbstractMatchingJob.rankContradictsName("Polygonum aviculare", Rank.SPECIES_AGGREGATE));
    assertFalse(AbstractMatchingJob.rankContradictsName("Polygonum aviculare", null));
  }

  /**
   * A rank that contradicts the name does not keep it from being found, unless the mode is STRICT.
   * See https://github.com/CatalogueOfLife/backend/issues/1649
   */
  @Test
  public void contradictingRankIgnored() throws Exception {
    var store = new UsageMatcherMemStore(1);
    addUsage(store, "fam", "Nemipteridae", "Regan, 1913", Rank.FAMILY, null);
    addUsage(store, "gen", "Oryctolagus", "Lilljeborg, 1873", Rank.GENUS, null);
    addUsage(store, "sp", "Oryctolagus cuniculus", "(Linnaeus, 1758)", Rank.SPECIES, "gen");
    addUsage(store, "ssp", "Oryctolagus cuniculus cuniculus", "(Linnaeus, 1758)", Rank.SUBSPECIES, "sp");
    try (var matcher = new UsageMatcher(1, ni, store, false)) {
      assertRankIgnored("ssp", "Oryctolagus cuniculus cuniculus (Linnaeus, 1758)", Rank.SPECIES, matcher);
      assertRankIgnored("fam", "Nemipteridae Regan, 1913", Rank.SPECIES, matcher);
      assertRankIgnored("gen", "Oryctolagus Lilljeborg, 1873", Rank.SPECIES, matcher);

      // a rank that fits the name stays binding
      var issues = new IssueContainer.Simple();
      var qm = match(SimpleName.sn(Rank.SPECIES, "Oryctolagus cuniculus", null), MatchingMode.RELAXED, issues, matcher);
      assertFalse(qm.rankIgnored());
      assertEquals("sp", qm.match().usage.getId());
      assertFalse(issues.contains(Issue.INCONSISTENT_NAME));
      // STRICT does not ignore a rank at all
      qm = match(SimpleName.sn(Rank.SPECIES, "Nemipteridae Regan, 1913", null), MatchingMode.STRICT, new IssueContainer.Simple(), matcher);
      assertFalse(qm.rankIgnored());
      assertFalse(qm.match().isMatch());
    }
  }

  private void assertRankIgnored(String id, String name, Rank rank, UsageMatcher matcher) {
    var issues = new IssueContainer.Simple();
    var qm = match(SimpleName.sn(rank, name, null), null, issues, matcher);
    assertTrue(name, qm.rankIgnored());
    assertEquals(name, id, qm.match().usage.getId());
    assertEquals(name, MatchType.EXACT, qm.match().type);
    assertNotEquals(rank, qm.query().getRank());
    assertTrue(issues.contains(Issue.INCONSISTENT_NAME));
  }

  private AbstractMatchingJob.QueryMatch match(SimpleName sn, MatchingMode mode, IssueContainer issues, UsageMatcher matcher) {
    return AbstractMatchingJob.matchQuery(sn, List.of(), issues, false, mode, interpreter, utils, matcher);
  }

  private void addUsage(UsageMatcherMemStore store, String id, String name, String authorship, Rank rank, String parent) {
    var sn = new SimpleNameCached(id, name, rank);
    sn.setAuthorship(authorship);
    sn.setParent(parent);
    sn.setStatus(TaxonomicStatus.ACCEPTED);
    sn.applyMatch(ni.match(SimpleName.sn(rank, name, authorship), true, false));
    store.add(sn);
  }

  /**
   * Interpretation rebuilds the name from the given rank, only the query tells whether it had a rank marker.
   * See https://github.com/CatalogueOfLife/backend/issues/1659
   */
  @Test
  public void unmarkedRank() {
    assertTrue(query(Rank.VARIETY, "Anas platyrhynchos domesticus").isUnmarkedRank());
    assertTrue(query(Rank.VARIETY, "Anas  platyrhynchos domesticus Linnaeus, 1758").isUnmarkedRank());
    assertTrue(query(Rank.SUBSPECIES, "Urtica dioica dioica").isUnmarkedRank());
    assertFalse(query(Rank.VARIETY, "Anas platyrhynchos var. domesticus").isUnmarkedRank());
    // the given rank overrules the marker of the name, which still is a marker
    assertFalse(query(Rank.VARIETY, "Anas platyrhynchos f. domesticus").isUnmarkedRank());
    // no rank given at all
    assertFalse(query(null, "Urtica dioica dioica").isUnmarkedRank());
    assertFalse(query(Rank.SPECIES, "Urtica dioica").isUnmarkedRank());
    // an epithet that only starts like another is no match
    assertFalse(AbstractMatchingJob.unmarkedRank("Aus bus cusa", Name.newBuilder()
      .genus("Aus").specificEpithet("bus").infraspecificEpithet("cus").rank(Rank.VARIETY).build()));
  }

  /**
   * The authors of the work a name was published in stay part of the query's authorship, the matcher compares them.
   * See https://github.com/CatalogueOfLife/backend/issues/1652
   */
  @Test
  public void inAuthorsKept() {
    assertEquals("(Natterer in Kuhl, 1817)", query(Rank.SPECIES, "Pipistrellus kuhlii (Natterer in Kuhl, 1817)").getAuthorship());
    var sn = SimpleName.sn(Rank.SPECIES, "Pipistrellus kuhlii", "(Natterer in Kuhl, 1817)");
    var q = AbstractMatchingJob.interpret(sn, List.of(), new IssueContainer.Simple(), interpreter, utils).get();
    assertEquals("(Natterer in Kuhl, 1817)", q.getAuthorship());
  }

  private QueryName query(Rank rank, String name) {
    return AbstractMatchingJob.interpret(SimpleName.sn(rank, name), List.of(), new IssueContainer.Simple(), interpreter, utils).get();
  }

  @Test
  public void internalNamesInsert() {
    var nu = new Taxon(interpreter.interpret(SimpleName.sn("Abies alba Mill."), new IssueContainer.Simple()).get().getName());
    var snc = utils.toSimpleNameClassified(nu, List.of());
    assertNotNull(snc.getNamesIndexId());
    assertEquals(1, ni.size());

    // an external query now finds the existing entry without adding another
    var q = AbstractMatchingJob.interpret(SimpleName.sn("Abies alba"), List.of(), new IssueContainer.Simple(), interpreter, utils);
    assertEquals(snc.getNamesIndexId(), q.get().getNamesIndexId());
    assertEquals(1, ni.size());
  }
}
