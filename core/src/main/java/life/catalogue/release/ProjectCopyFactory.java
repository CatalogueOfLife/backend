package life.catalogue.release;

import life.catalogue.api.model.Sector;
import life.catalogue.assembly.SyncFactory;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.*;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.jobs.SectorImportRetentionJob;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndex;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.validation.Validator;


public class ProjectCopyFactory {
  private final DatasetImportDao diDao;
  private final DatasetDao dDao;
  private final ReferenceDao rDao;
  private final NameDao nDao;
  private final SectorDao sDao;
  private final SectorImportDao siDao;
  private final NameUsageIndexService indexService;
  private final SqlSessionFactory factory;
  private final UsageMatcherFactory matcherFactory;
  private final SyncFactory syncFactory;
  private final ImageService imageService;
  private final CloseableHttpClient client;
  private final Validator validator;
  private final NameIndex nameIndex;
  private final ReleaseConfig cfg;
  private final URI apiURI;
  private final URI clbURI;
  private final JobExecutor jobExecutor;

  public ProjectCopyFactory(CloseableHttpClient client, NameIndex nameIndex, SyncFactory syncFactory, UsageMatcherFactory matcherFactory,
                            DatasetImportDao diDao, DatasetDao dDao, SectorImportDao siDao, ReferenceDao rDao, NameDao nDao, SectorDao sDao,
                            NameUsageIndexService indexService, ImageService imageService,
                            SqlSessionFactory factory, Validator validator,
                            ReleaseConfig cfg, URI apiURI, URI clbURI, @Nullable JobExecutor jobExecutor
  ) {
    this.client = client;
    this.nameIndex = nameIndex;
    this.syncFactory = syncFactory;
    this.matcherFactory = matcherFactory;
    this.diDao = diDao;
    this.dDao = dDao;
    this.rDao = rDao;
    this.nDao = nDao;
    this.sDao = sDao;
    this.siDao = siDao;
    this.indexService = indexService;
    this.imageService = imageService;
    this.factory = factory;
    this.validator = validator;
    this.cfg = cfg;
    this.apiURI = apiURI;
    this.clbURI = clbURI;
    this.jobExecutor = jobExecutor;
  }

  /**
   * Wires up the follow up job pruning sector sync history a release no longer pins, submitted to the
   * job executor only after the release itself has finished successfully - see
   * {@link AbstractProjectCopy#setRetentionJobFactory}.
   * Uses release.getDatasetKey() - the project key AbstractProjectCopy already resolved from whatever
   * project or release key the caller built it with - rather than a separately passed in key.
   */
  private void wireRetention(AbstractProjectCopy release, int userKey) {
    final int projectKey = release.getDatasetKey();
    release.setJobExecutor(jobExecutor);
    release.setRetentionJobFactory(() ->
      new SectorImportRetentionJob(userKey, factory, siDao.getFileMetricsDao(), projectKey, false));
  }

  /**
   * Extended release into a new dataset
   * @param releaseKey the dataset key of the base release this extended release should be based on.
   *
   * @throws IllegalArgumentException if the dataset is not a release
   */
  public XRelease buildExtendedRelease(final int releaseKey, final int userKey) {
    return buildExtendedRelease(releaseKey, userKey, false);
  }

  /**
   * @param force release even though a sector of the project holds the leftovers of a sync that did not finish
   */
  public XRelease buildExtendedRelease(final int releaseKey, final int userKey, final boolean force) {
    if (!force) {
      assertNoUnfinishedSyncs(factory, DatasetInfoCache.CACHE.info(releaseKey).keyOrProjectKey());
    }
    XRelease release = new XRelease(factory, syncFactory, matcherFactory, nameIndex, indexService, imageService,
      dDao, diDao, siDao, rDao, nDao, sDao, releaseKey, userKey,
      cfg, apiURI, clbURI, client, validator);
    wireRetention(release, userKey);
    return release;
  }

  public XRelease buildDebugXRelease(final int releaseKey, final int userKey) {
    XRelease release = new XReleaseDebug(factory, syncFactory, matcherFactory, nameIndex, indexService, imageService,
      dDao, diDao, siDao, rDao, nDao, sDao, releaseKey, userKey,
      cfg, apiURI, clbURI, client, validator);
    // deliberately NOT wired for retention: not wiring it only stops retention from being *triggered* by
    // this debug release. A live XReleaseDebug still creates an origin=XRELEASE, source_key=projectKey
    // dataset row, which DOES advance lastReleaseCreated (and so the retention cutoff) for the next real
    // run. Debug XReleases must therefore be deleted once they are no longer needed.
    return release;
  }

  /**
   * Release the catalogue into a new dataset
   * @param projectKey the draft catalogue to be released, e.g. 3 for the CoL draft
   *
   * @throws IllegalArgumentException if the dataset is not managed
   */
  public ProjectRelease buildRelease(final int projectKey, final int userKey) {
    return buildRelease(projectKey, userKey, false);
  }

  /**
   * Release the catalogue into a new dataset
   * @param projectKey the draft catalogue to be released, e.g. 3 for the CoL draft
   * @param force release even though a sector holds the leftovers of a sync that did not finish
   *
   * @throws IllegalArgumentException if the dataset is not managed, or if a sector is half synced and force is false
   */
  public ProjectRelease buildRelease(final int projectKey, final int userKey, final boolean force) {
    if (!force) {
      assertNoUnfinishedSyncs(factory, projectKey);
    }
    ProjectRelease release = new ProjectRelease(factory, indexService, imageService, diDao, dDao, rDao, nDao, sDao, projectKey, userKey,
      cfg, apiURI, clbURI, client, validator);
    wireRetention(release, userKey);
    return release;
  }

  /**
   * Refuses to release a project that holds the leftovers of a sector sync which never finished.
   *
   * Nothing rolls a sync back, so a sync that dies after deleteOld() leaves the project with whatever its
   * aborted copy had committed. In Sep 2026 that cost the COL project 25,712 usages of one ITIS sector and
   * went unnoticed through three release candidates, because the source metrics keep replaying the last
   * attempt sector.sync_attempt points at.
   *
   * This is deliberately a check on attempts, not on counts: it asks whether the job behind the sectors
   * current content finished, which is one cheap query, rather than recounting every sector against its
   * metrics. It is also deliberately overridable - a curator who has looked and decided the sector is fine
   * must be able to release without first re-running a multi hour sync.
   *
   * @throws IllegalArgumentException listing the offending sectors
   */
  public static void assertNoUnfinishedSyncs(SqlSessionFactory factory, int projectKey) {
    final List<Sector> broken;
    try (SqlSession session = factory.openSession(true)) {
      broken = session.getMapper(SectorMapper.class).listUnfinishedSyncs(projectKey);
    }
    if (!broken.isEmpty()) {
      throw new IllegalArgumentException("Project " + projectKey + " has " + broken.size()
        + " sector(s) whose content comes from a sync that did not finish, so their metrics describe a tree the project no longer holds: "
        + broken.stream().map(s -> s.getId() + " (source " + s.getSubjectDatasetKey() + ", attempt " + s.getSyncAttempt() + ")")
                .collect(Collectors.joining(", "))
        + ". Re-sync them, or release with force=true to accept the current content.");
    }
  }

  /**
   * Creates a duplicate of a managed project
   * @param projectKey the managed dataset to be copied
   *
   * @throws IllegalArgumentException if the dataset is not managed
   */
  public ProjectDuplication buildDuplication(int projectKey, int userKey) {
    // no retention wiring: a duplication is not a release and pins nothing
    return new ProjectDuplication(factory, indexService, diDao, dDao, validator, projectKey, userKey, cfg);
  }

}
