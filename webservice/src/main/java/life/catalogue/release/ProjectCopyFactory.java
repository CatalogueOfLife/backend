package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.assembly.SyncFactory;
import life.catalogue.dao.*;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.matching.UsageMatcherGlobal;

import jakarta.validation.Validator;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSessionFactory;


public class ProjectCopyFactory {
  private final ExportManager exportManager;
  private final DatasetImportDao diDao;
  private final DatasetDao dDao;
  private final ReferenceDao rDao;
  private final NameDao nDao;
  private final SectorDao sDao;
  private final SectorImportDao siDao;
  private final NameUsageIndexService indexService;
  private final DoiService doiService;
  private final DoiUpdater doiUpdater;
  private final SqlSessionFactory factory;
  private final SyncFactory syncFactory;
  private final ImageService imageService;
  private final CloseableHttpClient client;
  private final Validator validator;
  private final UsageMatcherGlobal matcher;
  private final WsServerConfig cfg;

  public ProjectCopyFactory(CloseableHttpClient client, UsageMatcherGlobal matcher, SyncFactory syncFactory,
                            DatasetImportDao diDao, DatasetDao dDao, SectorImportDao siDao, ReferenceDao rDao, NameDao nDao, SectorDao sDao,
                            ExportManager exportManager, NameUsageIndexService indexService, ImageService imageService,
                            DoiService doiService, DoiUpdater doiUpdater, SqlSessionFactory factory, Validator validator, WsServerConfig cfg) {
    this.client = client;
    this.matcher = matcher;
    this.syncFactory = syncFactory;
    this.exportManager = exportManager;
    this.diDao = diDao;
    this.dDao = dDao;
    this.rDao = rDao;
    this.nDao = nDao;
    this.sDao = sDao;
    this.siDao = siDao;
    this.indexService = indexService;
    this.imageService = imageService;
    this.doiService = doiService;
    this.doiUpdater = doiUpdater;
    this.factory = factory;
    this.validator = validator;
    this.cfg = cfg;
  }

  /**
   * Extended release into a new dataset
   * @param releaseKey the dataset key of the base release this extended release should be based on.
   *
   * @throws IllegalArgumentException if the dataset is not a release
   */
  public XRelease buildExtendedRelease(final int releaseKey, final int userKey) {
    return new XRelease(factory, syncFactory, matcher, indexService, imageService, dDao, diDao, siDao, rDao, nDao, sDao, releaseKey, userKey, cfg, client, exportManager, doiService, doiUpdater, validator);
  }

  /**
   * Release the catalogue into a new dataset
   * @param projectKey the draft catalogue to be released, e.g. 3 for the CoL draft
   *
   * @throws IllegalArgumentException if the dataset is not managed
   */
  public ProjectRelease buildRelease(final int projectKey, final int userKey) {
    return new ProjectRelease(factory, indexService, imageService, diDao, dDao, rDao, nDao, sDao, projectKey, userKey, cfg, client, exportManager, doiService, doiUpdater, validator);
  }

  /**
   * Creates a duplicate of a managed project
   * @param projectKey the managed dataset to be copied
   *
   * @throws IllegalArgumentException if the dataset is not managed
   */
  public ProjectDuplication buildDuplication(int projectKey, int userKey) {
    return new ProjectDuplication(factory, indexService, diDao, dDao, validator, projectKey, userKey, cfg);
  }

}
