package life.catalogue.release;

import life.catalogue.TestConfigs;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.api.vocab.JobStatus;
import life.catalogue.api.vocab.Users;
import life.catalogue.assembly.SyncFactoryRule;
import life.catalogue.common.io.Resources;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.EmailNotificationTemplateTest;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.config.MatchingConfig;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.DatasetSourceDao;
import life.catalogue.dao.SectorMetadataDao;
import life.catalogue.dao.ReferenceDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.jobs.SectorImportRetentionJob;
import life.catalogue.junit.NameMatchingRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.printer.PrinterFactory;
import life.catalogue.printer.TextTreePrinter;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.sql.Connection;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class ProjectReleaseIT extends ProjectBaseIT {

  @Rule
  public final TestRule chain = RuleChain
    .outerRule(new TestDataRule(IdProviderIT.PROJECT_DATA))
    .around(new ArchivingRule())
    .around(matchingRule);

  final int projectKey = IdProviderIT.PROJECT_DATA.key;

  @Test
  public void releaseMetadata() throws Exception {
    try(SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(false)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);

      Dataset d = dm.get(projectKey);
      d.setTitle("Catalogue of Life");
      d.setContributor(Agent.parse("Species 2000", "ITIS"));
      d.setEditor(List.of(
        new Agent("Yuri","Roskov"),
        new Agent("Geoff", "Ower"),
        new Agent("Thomas", "Orrell"),
        new Agent("David", "Nicolson")
      ));

      dm.update(d);
      session.commit();

      // update created to a fixed point in time for testing - needs JDBC
      Connection c = session.getConnection();
      var st = c.createStatement();
      st.execute("UPDATE dataset SET created = '2020-10-06 01:01:00' WHERE key = " + d.getKey());
      c.commit();
    }

    ProjectRelease pr = buildRelease();
    pr.prCfg.metadata.alias = "CoL{created,yy.M}";
    pr.initJob();
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
    assertEquals(JobStatus.FINISHED, release.getStatus());
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

    // test email templates
    EmailNotificationTemplateTest.testTemplates(release);
  }

  private static Sector sourceSector(int datasetKey, int id) {
    Sector s = new Sector();
    s.setDatasetKey(datasetKey);
    s.setId(id);
    s.setMode(Sector.Mode.SOURCE);
    s.setCreatedBy(Users.TESTER);
    s.setModifiedBy(Users.TESTER);
    return s;
  }

  private static Dataset metadata(String title) {
    Dataset d = new Dataset();
    d.setTitle(title);
    d.setCreatedBy(Users.TESTER);
    d.setModifiedBy(Users.TESTER);
    return d;
  }

  /**
   * Sector metadata is the one sector scoped table a release resolves rather than copies (#1273). The
   * publisher declared layer lives in the EXTERNAL source and is rewritten on every import, so the
   * release has to freeze the merge of it and the editor's override - otherwise re-importing the source
   * would silently rewrite what an already published release renders.
   */
  @Test
  public void releaseFreezesSectorMetadata() throws Exception {
    final var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    final var smDao = new SectorMetadataDao(factory, new DatasetSourceDao(factory));
    final int SRC = 100;
    final int SRC_SECTOR = 1044;

    try (SqlSession session = factory.openSession(true)) {
      SectorMapper sm = session.getMapper(SectorMapper.class);
      sm.createWithID(sourceSector(SRC, SRC_SECTOR));
      // the project sector absorbs the source's declaration
      Sector s = sm.get(DSID.of(projectKey, 1));
      s.setSubjectSectorId(SRC_SECTOR);
      sm.update(s);
      // and a second sector that has metadata but never contributes any data, so it gets dropped by
      // deleteOrphans. The release must still complete rather than trip the metadata foreign key.
      Sector empty = sourceSector(projectKey, 99);
      empty.setMode(Sector.Mode.ATTACH);
      empty.setSubjectDatasetKey(SRC);
      sm.createWithID(empty);
    }

    Dataset declared = metadata("World Porifera Database");
    declared.setAlias("WPD");
    declared.setEditor(List.of(new Agent("Nicole", "de Voogd")));
    Citation c = new Citation();
    c.setId("wpd");
    c.setTitle("de Voogd, N.J. et al. (2026)");
    declared.setSource(List.of(c));
    smDao.putPatch(DSID.of(SRC, SRC_SECTOR), declared, Users.TESTER);

    smDao.putPatch(DSID.of(projectKey, 1), metadata("Porifera, CoL edition"), Users.TESTER);
    smDao.putPatch(DSID.of(projectKey, 99), metadata("Never released"), Users.TESTER);

    ProjectRelease release = buildRelease();
    release.run();
    assertEquals(JobStatus.FINISHED, release.getStatus());
    final int relKey = release.newDatasetKey;

    // the frozen row is the merge: the editor's title over everything the publisher declared
    Dataset frozen = smDao.getPatch(DSID.of(relKey, 1));
    assertNotNull("sector metadata was not frozen into the release", frozen);
    assertEquals("Porifera, CoL edition", frozen.getTitle());
    assertEquals("WPD", frozen.getAlias());
    assertEquals(List.of(new Agent("Nicole", "de Voogd")), frozen.getEditor());
    assertEquals(1, frozen.getSource().size());
    assertEquals("wpd", frozen.getSource().get(0).getId());

    // a release resolves against its own frozen dataset_source, so the sector page still inherits
    Dataset resolved = smDao.resolve(DSID.of(relKey, 1));
    assertEquals("Porifera, CoL edition", resolved.getTitle());
    assertEquals("WPD", resolved.getAlias());

    // the sector that never produced data is gone, metadata and all
    assertNull(smDao.getPatch(DSID.of(relKey, 99)));
    // but the project keeps its own copy - the release froze, it did not move
    assertNotNull(smDao.getPatch(DSID.of(projectKey, 99)));
    assertEquals("Porifera, CoL edition", smDao.getPatch(DSID.of(projectKey, 1)).getTitle());

    // now the source moves on, as it does on every import. The release must not budge.
    Dataset changed = metadata("Renamed by the publisher");
    changed.setAlias("XXX");
    smDao.putPatch(DSID.of(SRC, SRC_SECTOR), changed, Users.TESTER);

    assertEquals("WPD", smDao.getPatch(DSID.of(relKey, 1)).getAlias());
    assertEquals("Porifera, CoL edition", smDao.resolve(DSID.of(relKey, 1)).getTitle());
    assertEquals("WPD", smDao.resolve(DSID.of(relKey, 1)).getAlias());
    // while the project, which resolves live, does follow it
    assertEquals("XXX", smDao.resolve(DSID.of(projectKey, 1)).getAlias());
  }

  private ProjectRelease buildRelease() {
    ReleaseConfig cfg = new ReleaseConfig();
    cfg.restart = null;
    return projectCopyFactory.buildRelease(projectKey, Users.TESTER);
  }

  /**
   * The default {@link #projectCopyFactory} inherited from {@link ProjectBaseIT} is wired with a null
   * JobExecutor, so it never exercises the retention trigger in {@link AbstractProjectCopy#runWithLock}.
   * Builds a fresh factory identical to {@link ProjectBaseIT#init()}, but with a Mockito mock JobExecutor
   * instead - never a real one, which would actually prune the test database.
   */
  private ProjectCopyFactory buildFactoryWithMockExecutor(JobExecutor jobExecutor) {
    TestConfigs cfg = TestConfigs.build();
    cfg.apiURI = null;
    cfg.clbURI = URI.create("https://www.dev.checklistbank.org");
    var rdao = mock(ReferenceDao.class);
    var matcherFactory = new UsageMatcherFactory(new MatchingConfig(), NameMatchingRule.getIndex(),
      SqlSessionFactoryRule.getSqlSessionFactory(), jobExecutor);
    return new ProjectCopyFactory(null, NameMatchingRule.getIndex(), SyncFactoryRule.getFactory(), matcherFactory,
      syncFactoryRule.getDiDao(), dDao, syncFactoryRule.getSiDao(), rdao, syncFactoryRule.getnDao(), syncFactoryRule.getSdao(),
      NameUsageIndexService.passThru(), ImageService.passThru(), SqlSessionFactoryRule.getSqlSessionFactory(), validator,
      cfg.release, cfg.apiURI, cfg.clbURI, jobExecutor
    );
  }

  /**
   * The whole point of wiring a retention job to a release: a finished release must submit exactly one
   * real (non dry run) {@link SectorImportRetentionJob} for the project it released, to the job executor.
   */
  @Test
  public void releaseSubmitsRetentionJob() throws Exception {
    var jobExecutor = mock(JobExecutor.class);
    ProjectRelease release = buildFactoryWithMockExecutor(jobExecutor).buildRelease(projectKey, Users.TESTER);
    release.run();
    assertEquals(JobStatus.FINISHED, release.getStatus());

    var captor = ArgumentCaptor.forClass(BackgroundJob.class);
    verify(jobExecutor, times(1)).submit(captor.capture());
    var rj = (SectorImportRetentionJob) captor.getValue();
    assertFalse("the release trigger must prune for real, not dry run", rj.isDryRun());
    assertEquals(projectKey, rj.getDatasetKey());
  }

  /**
   * A duplication is not a release: {@link ProjectCopyFactory#buildDuplication} never wires retention at
   * all, so running one to completion must never touch the job executor.
   */
  @Test
  public void duplicationSubmitsNoRetentionJob() throws Exception {
    var jobExecutor = mock(JobExecutor.class);
    ProjectDuplication dupl = buildFactoryWithMockExecutor(jobExecutor).buildDuplication(projectKey, Users.TESTER);
    dupl.run();
    assertEquals(JobStatus.FINISHED, dupl.getStatus());
    verifyNoInteractions(jobExecutor);
  }

}