package life.catalogue.release;

import com.google.common.eventbus.EventBus;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import org.apache.ibatis.session.SqlSession;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.File;
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
    .outerRule(new TestDataRule(IdProviderTest.PROJECT_DATA))
    .around(matchingRule);

  DatasetImportDao diDao;
  DatasetDao dDao;

  final int projectKey = IdProviderTest.PROJECT_DATA.key;
  
  @Before
  public void init()  {
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
    EventBus bus = mock(EventBus.class);
    dDao = new DatasetDao(PgSetupRule.getSqlSessionFactory(), null, ImageService.passThru(), diDao, NameUsageIndexService.passThru(), null, bus);
  }
  
  @Test
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
    return ReleaseManager.release(PgSetupRule.getSqlSessionFactory(), matchingRule.getIndex(), NameUsageIndexService.passThru(), diDao, dDao, ImageService.passThru(), projectKey, Users.TESTER, cfg);
  }
  
  @Test
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