package life.catalogue.release;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.User;
import life.catalogue.concurrent.NamedThreadFactory;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.NameDao;
import life.catalogue.doi.DoiUpdater;
import life.catalogue.doi.service.DoiService;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.exporter.ExportManager;
import life.catalogue.img.ImageService;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Validator;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ProjectCopyFactory {
  private final ExportManager exportManager;
  private final DatasetImportDao diDao;
  private final DatasetDao dDao;
  private final NameDao nDao;
  private final NameUsageIndexService indexService;
  private final DoiService doiService;
  private final DoiUpdater doiUpdater;
  private final SqlSessionFactory factory;
  private final ImageService imageService;
  private final CloseableHttpClient client;
  private final Validator validator;
  private final WsServerConfig cfg;

  public ProjectCopyFactory(CloseableHttpClient client, DatasetImportDao diDao, DatasetDao dDao, NameDao nDao, ExportManager exportManager, NameUsageIndexService indexService,
                            ImageService imageService, DoiService doiService, DoiUpdater doiUpdater, SqlSessionFactory factory, Validator validator, WsServerConfig cfg) {
    this.client = client;
    this.exportManager = exportManager;
    this.diDao = diDao;
    this.dDao = dDao;
    this.nDao = nDao;
    this.indexService = indexService;
    this.imageService = imageService;
    this.doiService = doiService;
    this.doiUpdater = doiUpdater;
    this.factory = factory;
    this.validator = validator;
    this.cfg = cfg;
  }

  /**
   * Release the catalogue into a new dataset
   * @param projectKey the draft catalogue to be released, e.g. 3 for the CoL draft
   *
   * @throws IllegalArgumentException if the dataset is not managed
   */
  public ProjectRelease buildRelease(final int projectKey, final int userKey) {
    return new ProjectRelease(factory, indexService, diDao, dDao, nDao, imageService, projectKey, userKey, cfg, client, exportManager, doiService, doiUpdater, validator);
  }

  /**
   * Creates a duplicate of a managed project
   * @param projectKey the managed dataset to be copied
   *
   * @throws IllegalArgumentException if the dataset is not managed
   */
  public ProjectDuplication buildDuplication(int projectKey, int userKey) {
    return new ProjectDuplication(factory, indexService, diDao, dDao, validator, projectKey, userKey);
  }

}
