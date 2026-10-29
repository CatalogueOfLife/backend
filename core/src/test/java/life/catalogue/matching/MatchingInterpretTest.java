package life.catalogue.matching;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.Taxon;
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
