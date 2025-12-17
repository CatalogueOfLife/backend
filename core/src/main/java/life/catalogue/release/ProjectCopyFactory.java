package life.catalogue.release;

import life.catalogue.assembly.SyncFactory;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.dao.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;
import life.catalogue.matching.UsageMatcherFactory;
import life.catalogue.matching.nidx.NameIndex;

import java.net.URI;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.validation.Validator;


public class ProjectCopyFactory {
  private final ExportManager exportManager;
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

  public ProjectCopyFactory(CloseableHttpClient client, NameIndex nameIndex, SyncFactory syncFactory, UsageMatcherFactory matcherFactory,
                            DatasetImportDao diDao, DatasetDao dDao, SectorImportDao siDao, ReferenceDao rDao, NameDao nDao, SectorDao sDao,
                            ExportManager exportManager, NameUsageIndexService indexService, ImageService imageService,
                            SqlSessionFactory factory, Validator validator,
                            ReleaseConfig cfg, URI apiURI, URI clbURI
  ) {
    this.client = client;
    this.nameIndex = nameIndex;
    this.syncFactory = syncFactory;
    this.matcherFactory = matcherFactory;
    this.exportManager = exportManager;
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
  }

  /**
   * Extended release into a new dataset
   * @param releaseKey the dataset key of the base release this extended release should be based on.
   *
   * @throws IllegalArgumentException if the dataset is not a release
   */
  public XRelease buildExtendedRelease(final int releaseKey, final int userKey) {
    return new XRelease(factory, syncFactory, matcherFactory, nameIndex, indexService, imageService, dDao, diDao, siDao, rDao, nDao, sDao, releaseKey, userKey,
      cfg, apiURI, clbURI, client, exportManager, validator);
  }

  public XRelease buildDebugXRelease(final int releaseKey, final int userKey) {
    return new XReleaseDebug(factory, syncFactory, matcherFactory, nameIndex, indexService, imageService, dDao, diDao, siDao, rDao, nDao, sDao, releaseKey, userKey,
      cfg, apiURI, clbURI, client, exportManager, validator);
  }

  /**
   * Release the catalogue into a new dataset
   * @param projectKey the draft catalogue to be released, e.g. 3 for the CoL draft
   *
   * @throws IllegalArgumentException if the dataset is not managed
   */
  public ProjectRelease buildRelease(final int projectKey, final int userKey) {
    return new ProjectRelease(factory, indexService, imageService, diDao, dDao, rDao, nDao, sDao, projectKey, userKey,
      cfg, apiURI, clbURI, client, exportManager, validator);
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
