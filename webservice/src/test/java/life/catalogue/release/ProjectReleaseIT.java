package life.catalogue.release;

import com.google.common.eventbus.EventBus;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.File;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class ProjectReleaseIT {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final TreeRepoRule treeRepoRule = new TreeRepoRule();

  NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(new TestDataRule(IdProviderIT.PROJECT_DATA))
    .around(matchingRule);

  DatasetImportDao diDao;
  DatasetDao dDao;

  final int projectKey = IdProviderIT.PROJECT_DATA.key;
  ReleaseManager releaseManager;

  @Before
  public void init()  {
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    EventBus bus = mock(EventBus.class);
    dDao = new DatasetDao(PgSetupRule.getSqlSessionFactory(), null, ImageService.passThru(), diDao, NameUsageIndexService.passThru(), null, bus);
    releaseManager = new ReleaseManager(diDao, dDao, matchingRule.getIndex(), NameUsageIndexService.passThru(), ImageService.passThru(), PgSetupRule.getSqlSessionFactory(), new ReleaseConfig());
  }

  @Test
  public void releaseMetadata() throws Exception {
    try(SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(false)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);

      DatasetSettings ds = dm.getSettings(projectKey);
      ds.put(Setting.RELEASE_ALIAS_TEMPLATE, "CoL{created,yy.M}");
      ds.put(Setting.RELEASE_TITLE_TEMPLATE, "Catalogue of Life - Release {importAttempt}, {created,MMMM yyyy}");
      ds.put(Setting.RELEASE_CITATION_TEMPLATE, "{editors} ({created,yyyy}). Species 2000 & ITIS Catalogue of Life, {created,ddd MMMM yyyy}. Digital resource at www.catalogueoflife.org. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.");

      Dataset d = dm.get(projectKey);
      d.setTitle("Catalogue of Life");
      d.setOrganisations(Organisation.parse("Species 2000", "ITIS"));
      d.setEditors(List.of(
        new Person("Yuri","Roskov"),
        new Person("Geoff", "Ower"),
        new Person("Thomas", "Orrell"),
        new Person("David", "Nicolson")
      ));

      dm.updateAll(new DatasetWithSettings(d, ds));
      session.commit();

      // update created to a fixed point in time for testing - needs JDBC
      Connection c = session.getConnection();
      var st = c.createStatement();
      st.execute("UPDATE dataset SET created = '2020-10-06 01:01:00' WHERE key = " + d.getKey());
      c.commit();
    }

    ProjectRelease pr = buildRelease();
    assertEquals("CoL20.10", pr.newDataset.getAlias());
    assertEquals("Catalogue of Life - Release 4, October 2020", pr.newDataset.getTitle());
    assertEquals("Roskov Y., Ower G., Orrell T., Nicolson D. (eds.) (2020). Species 2000 & ITIS Catalogue of Life, 6th October 2020. Digital resource at www.catalogueoflife.org. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.",
      pr.newDataset.getCitation()
    );
  }

  @Test
  @Ignore
  public void release() throws Exception {

    ProjectRelease release = buildRelease();
    Map.of(1, "9999", // pref ID out of sequence range
      17, "A",
      9998, "33" // pref ID in sequence range
    );
    release.run();
    assertEquals(ImportState.FINISHED, release.getMetrics().getState());

    DSID<String> key = DSID.of(release.newDatasetKey, "");
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      // canonical match
      NameUsageBase u = num.get(key.id("R"));
      assertEquals("Canis aureus", u.getLabel());

      // authorship match
      u = num.get(key.id("C"));
      final String homoID = u.getName().getHomotypicNameId();
      assertEquals("Lynx lynx (Linnaeus, 1758)", u.getLabel());
      assertEquals(u.getName().getId(), u.getName().getHomotypicNameId());

      // TODO: new authorship matching previous canonical name

      // rufus -> rufa
      //TODO: check why? Should this not be E ???
      u = num.get(key.id("B5"));
      assertEquals("Felis rufa", u.getLabel());

      // baileyi -> baileii
      u = num.get(key.id("F"));
      assertEquals("Lynx rufus baileii", u.getLabel());

      // new id, starting with B3 as the first new one
      u = num.get(key.id("B4"));
      assertEquals("Felis lynx Linnaeus, 1758", u.getLabel());
      assertEquals(homoID, u.getName().getHomotypicNameId());

      // check metrics
      DatasetImportDao diDao = new DatasetImportDao(release.factory, new File("/tmp"));
      DatasetImport imp = diDao.getLast(projectKey);
      assertEquals(25, imp.getUsagesCount());
    }
  }

  private ProjectRelease buildRelease() {
    ReleaseConfig cfg = new ReleaseConfig();
    cfg.restart = false;
    return releaseManager.buildRelease(projectKey, Users.TESTER);
  }
  
  @Test
  @Ignore("We deactivated the blocking of parallel releases. This runs anyways in an executor with limited threads")
  public void releaseConcurrently() throws Exception {
    Thread t1 = new Thread(buildRelease());
    t1.start();
  
    try {
      buildRelease();
      fail("Parallel releases should not be allowed!");
    } catch (IllegalArgumentException e) {
      // expected
    }
    // wait for release to be done and run another one
    t1.join();
  
    Thread t2 = new Thread(buildRelease());
    t2.start();
    t2.join();
  }
  
}