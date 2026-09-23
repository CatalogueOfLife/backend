package life.catalogue.assembly;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.Sector;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
import life.catalogue.junit.*;
import life.catalogue.release.XReleaseConfig;

import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.*;

/**
 * Merging the publishedIn of a source name into project names which already cite a reference, see #1606.
 * The project names g1, 1 and 2 of the draft_name_upd test data cite p3, p1 and the author &amp; year stub p2,
 * all merged with the article Q56209236 of dataset 100 on a different, the same and another page.
 *
 * A class of its own with a single test, as the names index is kept across tests while the test data is not.
 */
public class SectorSyncPublishedInIT extends SectorSyncTestBase {

  final static SqlSessionFactoryRule pg = new PgSetupRule();
  final static TreeRepoRule treeRepoRule = new TreeRepoRule();
  final static NameMatchingRule matchingRule = new NameMatchingRule(true);
  final static SyncFactoryRule syncFactoryRule = new SyncFactoryRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pg)
    .around(treeRepoRule)
    .around(matchingRule)
    .around(syncFactoryRule);

  public final TestDataRule dataRule = TestDataRule.draftNameUpd();

  @Rule
  public final TestRule testRules = RuleChain
    .outerRule(dataRule)
    .around(matchingRule);

  @Test
  public void mergePublishedIn() {
    XReleaseConfig cfg = new XReleaseConfig();
    TreeMergeHandlerConfig mergeCfg = new TreeMergeHandlerConfig(PgSetupRule.getSqlSessionFactory(), cfg, Datasets.COL, Users.TESTER);

    Sector s100 = new Sector();
    s100.setDatasetKey(Datasets.COL);
    s100.applyUser(Users.TESTER);
    s100.setMode(Sector.Mode.MERGE);
    s100.setSubjectDatasetKey(100);
    s100.setEntities(Set.of(EntityType.NAME, EntityType.REFERENCE));
    s100.setPriority(1);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      session.getMapper(SectorMapper.class).create(s100);
    }
    sync(s100, mergeCfg);
    assertPublishedIn(s100);

    // the project names now cite references of the sector - syncing it again must still work and yield the same
    sync(s100, mergeCfg);
    assertPublishedIn(s100);
  }

  private void assertPublishedIn(Sector s100) {
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var nm = session.getMapper(NameMapper.class);
      var rm = session.getMapper(ReferenceMapper.class);
      var vsm = session.getMapper(VerbatimSourceMapper.class);

      // the same page: the name gets the page link, its own reference the DOI of the same article
      var key = DSID.of(Datasets.COL, "1");
      Name n = nm.get(key);
      assertEquals("p1", n.getPublishedInId());
      assertEquals("12", n.getPublishedInPage());
      assertEquals("https://www.biodiversitylibrary.org/page/12", n.getPublishedInPageLink());
      Reference r = rm.get(DSID.of(Datasets.COL, "p1"));
      assertEquals("de Laubenfels, M.W. (1932). The marine and freshwater sponges of California. Proceedings of the United States National Museum, 81: 1-140.", r.getCitation());
      assertEquals("10.5479/si.00963801.81-2927.1", r.getCsl().getDOI());
      assertEquals("https://bionames.org/references/de9e5aed56f49c4406cf87f2fbf8e620", r.getCsl().getURL());
      var vs = vsm.addSources(vsm.getByName(key));
      assertTrue(DSID.equals(DSID.of(100, "1"), vs.getSecondarySources().get(InfoGroup.PUBLISHED_IN)));

      // an author & year stub is replaced for that one name, but left alone for all others citing it
      n = nm.get(DSID.of(Datasets.COL, "2"));
      assertNotEquals("p2", n.getPublishedInId());
      r = rm.get(DSID.of(Datasets.COL, n.getPublishedInId()));
      assertEquals(s100.getId(), r.getSectorKey());
      assertTrue(r.getCitation().startsWith("The marine and freshwater sponges of California. (1932)."));
      assertEquals("40", n.getPublishedInPage());
      assertEquals("https://www.biodiversitylibrary.org/page/40", n.getPublishedInPageLink());
      r = rm.get(DSID.of(Datasets.COL, "p2"));
      assertEquals("de Laubenfels. (1932).", r.getCitation());
      assertTrue(r.getCsl() == null || r.getCsl().getDOI() == null);

      // a different page contradicts the source: nothing is merged
      n = nm.get(DSID.of(Datasets.COL, "g1"));
      assertEquals("p3", n.getPublishedInId());
      assertNull(n.getPublishedInPage());
      assertNull(n.getPublishedInPageLink());
      r = rm.get(DSID.of(Datasets.COL, "p3"));
      assertTrue(r.getCsl() == null || r.getCsl().getDOI() == null);
    }
  }
}
