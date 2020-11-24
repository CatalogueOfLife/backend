package life.catalogue.release;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.TreeRepoRule;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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

  final int projectKey = IdProviderTest.PROJECT_DATA.key;
  
  @Before
  public void init()  {
    diDao = new DatasetImportDao(PgSetupRule.getSqlSessionFactory(), treeRepoRule.getRepo());
  }
  
  @Test
  public void release() throws Exception {
    ProjectRelease release = buildRelease();
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
      u = num.get(key.id("E"));
      assertEquals("Felis rufa", u.getLabel());

      // baileyi -> baileii
      u = num.get(key.id("F"));
      assertEquals("Lynx rufus baileii", u.getLabel());

      // new id
      u = num.get(key.id("33"));
      assertEquals("Felis lynx Linnaeus, 1758", u.getLabel());
      assertEquals(homoID, u.getName().getHomotypicNameId());
    }
  }
  
  private ProjectRelease buildRelease() {
    return ReleaseManager.release(PgSetupRule.getSqlSessionFactory(), matchingRule.getIndex(), NameUsageIndexService.passThru(), diDao, ImageService.passThru(), projectKey, Users.TESTER, true);
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