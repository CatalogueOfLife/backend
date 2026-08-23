package life.catalogue.release;

import life.catalogue.assembly.SyncFactory;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.*;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.jobs.SectorImportRetentionJob;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndex;

import java.net.URI;

import javax.annotation.Nullable;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
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
    // deliberately NOT wired for retention: a debug XRelease is a throwaway that must not consume
    // the retention window a real release defines
    return release;
  }

  /**
   * Release the catalogue into a new dataset
   * @param projectKey the draft catalogue to be released, e.g. 3 for the CoL draft
   *
   * @throws IllegalArgumentException if the dataset is not managed
   */
  public ProjectRelease buildRelease(final int projectKey, final int userKey) {
    ProjectRelease release = new ProjectRelease(factory, indexService, imageService, diDao, dDao, rDao, nDao, sDao, projectKey, userKey,
      cfg, apiURI, clbURI, client, validator);
    wireRetention(release, userKey);
    return release;
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
