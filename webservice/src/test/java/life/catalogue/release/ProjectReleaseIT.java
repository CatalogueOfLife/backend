package life.catalogue.release;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.Resources;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.Connection;
import java.util.List;

import life.catalogue.printer.PrinterFactory;
import life.catalogue.printer.TextTreePrinter;

import org.apache.ibatis.session.SqlSession;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.*;

public class ProjectReleaseIT extends ProjectBaseIT {
  private static final Logger LOG = LoggerFactory.getLogger(ProjectReleaseIT.class);

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(new TestDataRule(IdProviderIT.PROJECT_DATA))
    .around(matchingRule);

  final int projectKey = IdProviderIT.PROJECT_DATA.key;

  @Test
  public void releaseMetadata() throws Exception {
    try(SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(false)) {
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

  private void assertSameTree(int datasetKey, String resourceName) throws IOException {
    String tree = readTree(datasetKey);
    System.out.println(tree);
    String expected = Resources.toString("assembly-trees/" + resourceName);
    assertEquals(expected.trim(), tree.trim());
  }
  public static String readTree(int datasetKey) throws IOException {
    Writer writer = new StringWriter();
    TreeTraversalParameter ttp = TreeTraversalParameter.dataset(datasetKey);
    var printer = PrinterFactory.dataset(TextTreePrinter.class, ttp, SqlSessionFactoryRule.getSqlSessionFactory(), writer);
    printer.showIDs();
    printer.print();
    String tree = writer.toString().trim();
    assertFalse("Empty tree, probably no root node found", tree.isEmpty());
    return tree;
  }
  @Test
  public void release() throws Exception {
    ProjectRelease release = buildRelease();
    release.run();
    assertEquals(ImportState.FINISHED, release.getMetrics().getState());
    assertSameTree(release.newDatasetKey, "release-expected.tree");

    DSID<String> key = DSID.root(release.newDatasetKey);
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
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

      // also test publishing the release
      var rel = session.getMapper(DatasetMapper.class).get(release.newDatasetKey);
      assertTrue(rel.isPrivat());
    }
  }

  private ProjectRelease buildRelease() {
    ReleaseConfig cfg = new ReleaseConfig();
    cfg.restart = null;
    return projectCopyFactory.buildRelease(projectKey, Users.TESTER);
  }
  
}