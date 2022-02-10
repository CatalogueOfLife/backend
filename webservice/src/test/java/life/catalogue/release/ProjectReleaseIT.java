package life.catalogue.release;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.db.NameMatchingRule;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;

import java.io.File;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;

public class ProjectReleaseIT extends ProjectBaseIT {

  NameMatchingRule matchingRule = new NameMatchingRule();

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(new TestDataRule(IdProviderIT.PROJECT_DATA))
    .around(matchingRule);

  final int projectKey = IdProviderIT.PROJECT_DATA.key;

  @Test
  public void releaseMetadata() throws Exception {
    try(SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(false)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);

      DatasetSettings ds = dm.getSettings(projectKey);
      ds.put(Setting.RELEASE_ALIAS_TEMPLATE, "CoL{created,yy.M}");

      Dataset d = dm.get(projectKey);
      d.setTitle("Catalogue of Life");
      d.setContributor(Agent.parse("Species 2000", "ITIS"));
      d.setEditor(List.of(
        new Agent("Yuri","Roskov"),
        new Agent("Geoff", "Ower"),
        new Agent("Thomas", "Orrell"),
        new Agent("David", "Nicolson")
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
    assertEquals("Catalogue of Life", pr.newDataset.getTitle());
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

      // TODO: check more outcomes

      // baileyi -> baileii
      u = num.get(key.id("F"));
      assertEquals("Lynx rufus baileii", u.getLabel());

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
  
}