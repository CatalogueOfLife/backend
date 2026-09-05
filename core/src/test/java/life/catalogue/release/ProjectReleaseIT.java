package life.catalogue.release;

import life.catalogue.TestConfigs;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.EntityType;
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
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.ReferenceDao;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetSourceMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.VerbatimSourceMapper;
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
  /**
   * A hierarchy sector is configured against a project but reads one of that projects releases, which is what the
   * release has to archive as its source - the project has no archived metadata for the attempt that was synced,
   * and used to yield a source dataset with a null key that killed the release on the not null constraint.
   */
  @Test
  public void releaseArchivesTheResolvedReleaseAsSource() throws Exception {
    final int sourceProjectKey = 100;
    final Integer sourceReleaseKey;

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(false)) {
      var dm = session.getMapper(DatasetMapper.class);

      // the release of the source project that the sync really read
      Dataset rel = new Dataset();
      rel.setTitle("Source #1, release");
      rel.setType(DatasetType.OTHER);
      rel.setOrigin(DatasetOrigin.RELEASE);
      rel.setSourceKey(sourceProjectKey);
      rel.applyUser(Users.TESTER);
      dm.create(rel);
      dm.updateLastImport(rel.getKey(), 7, null);
      sourceReleaseKey = rel.getKey();

      // the provenance the sync wrote: the sectors data came from that release
      session.getMapper(VerbatimSourceMapper.class)
             .create(new VerbatimSource(projectKey, 1, 1, sourceReleaseKey, "x1", EntityType.NAME_USAGE));

      // the sector is configured against the project and synced its attempt 1, which has since moved on to 2 -
      // so the source metadata is looked for in dataset_archive, where a project never has any
      Connection c = session.getConnection();
      var st = c.createStatement();
      st.execute("UPDATE dataset SET origin='PROJECT' WHERE key = " + sourceProjectKey);
      st.execute("UPDATE sector SET sync_attempt=1, dataset_attempt=1 WHERE dataset_key=" + projectKey + " AND id=1");
      st.execute("UPDATE dataset SET attempt=2 WHERE key = " + sourceProjectKey);
      session.commit();
      c.commit();
    }
    DatasetInfoCache.CACHE.clear();

    ProjectRelease release = buildRelease();
    release.run();
    assertEquals(JobStatus.FINISHED, release.getStatus());

    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      // the sector copied into the release points at the release that was read, not at the project
      Sector s = session.getMapper(SectorMapper.class).get(DSID.of(release.newDatasetKey, 1));
      assertEquals(sourceReleaseKey, s.getSubjectDatasetKey());
      assertEquals(Integer.valueOf(7), s.getDatasetAttempt());
      // ... while the project keeps its configuration
      assertEquals(Integer.valueOf(sourceProjectKey),
        session.getMapper(SectorMapper.class).get(DSID.of(projectKey, 1)).getSubjectDatasetKey());

      var sources = session.getMapper(DatasetSourceMapper.class).listReleaseSources(release.newDatasetKey, true);
      assertEquals(1, sources.size());
      assertEquals(sourceReleaseKey, sources.get(0).getKey());
    }
  }

  @Test
  public void duplicationSubmitsNoRetentionJob() throws Exception {
    var jobExecutor = mock(JobExecutor.class);
    ProjectDuplication dupl = buildFactoryWithMockExecutor(jobExecutor).buildDuplication(projectKey, Users.TESTER);
    dupl.run();
    assertEquals(JobStatus.FINISHED, dupl.getStatus());
    verifyNoInteractions(jobExecutor);
  }

}