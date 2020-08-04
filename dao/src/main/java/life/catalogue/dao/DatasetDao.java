package life.catalogue.dao;

import com.google.common.eventbus.EventBus;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.UserPermissionChanged;
import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.db.DatasetProcessable;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.img.ImageService;
import life.catalogue.img.LogoUpdateJob;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;

public class DatasetDao extends DataEntityDao<Integer, Dataset, DatasetMapper> {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(DatasetDao.class);

  private final DownloadUtil downloader;
  private final ImageService imgService;
  private final BiFunction<Integer, String, File> scratchFileFunc;
  private final DatasetImportDao diDao;
  private final NameUsageIndexService indexService;
  private final EventBus bus;

  /**
   * @param scratchFileFunc function to generate a scrach dir for logo updates
   */
  public DatasetDao(SqlSessionFactory factory,
                    DownloadUtil downloader,
                    ImageService imgService,
                    DatasetImportDao diDao,
                    NameUsageIndexService indexService,
                    BiFunction<Integer, String, File> scratchFileFunc,
                    EventBus bus) {
    super(false, factory, DatasetMapper.class);
    this.downloader = downloader;
    this.imgService = imgService;
    this.scratchFileFunc = scratchFileFunc;
    this.diDao = diDao;
    this.indexService = indexService;
    this.bus = bus;
  }
  
  public ResultPage<Dataset> list(Page page) {
    return super.list(DatasetMapper.class, page);
  }

  public DatasetSettings getSettings(int key) {
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      return dm.getSettings(key);
    }
  }

  public void putSettings(int key, DatasetSettings settings, int userKey) {
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      dm.updateSettings(key, settings, userKey);
      session.commit();
    }
  }

  public Dataset latestRelease(int projectKey) {
    try (SqlSession session = factory.openSession()){
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      Integer key = dm.latestRelease(projectKey);
      if (key == null) {
        throw new NotFoundException("Dataset " + projectKey + " was never released");
      }
      return dm.get(key);
    }
  }

  public ResultPage<Dataset> search(@Nullable DatasetSearchRequest nullableRequest, @Nullable Integer userKey, @Nullable Page page) {
    page = page == null ? new Page() : page;
    final DatasetSearchRequest req = nullableRequest == null ? new DatasetSearchRequest() : nullableRequest;
    if (req.getSortBy() == null) {
      if (!StringUtils.isBlank(req.getQ())) {
        req.setSortBy(DatasetSearchRequest.SortBy.RELEVANCE);
      } else {
        req.setSortBy(DatasetSearchRequest.SortBy.KEY);
      }
    } else if (req.getSortBy() == DatasetSearchRequest.SortBy.RELEVANCE && StringUtils.isBlank(req.getQ())) {
      req.setQ(null);
      req.setSortBy(DatasetSearchRequest.SortBy.KEY);
    }
    
    try (SqlSession session = factory.openSession()){
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      List<Dataset> result = dm.search(req, userKey, page);
      return new ResultPage<>(page, result, () -> dm.count(req, userKey));
    }
  }
  
  @Override
  protected void deleteBefore(Integer key, Dataset old, int user, DatasetMapper mapper, SqlSession session) {
    // remove decisions, sectors, estimates, dataset patches
    for (Class<DatasetProcessable<?>> mClass : new Class[]{SectorMapper.class, DecisionMapper.class, EstimateMapper.class, DatasetPatchMapper.class}) {
      LOG.info("Delete {}s for dataset {}", mClass.getSimpleName().substring(0, mClass.getSimpleName().length() - 6), key);
      session.getMapper(mClass).deleteByDataset(key);
    }
    // remove project source dataset archives
    LOG.info("Delete project source dataset archives for dataset {}", key);
    session.getMapper(DatasetArchiveMapper.class).deleteByDataset(key);
    // remove import & sync history
    LOG.info("Delete sector sync history for dataset {}", key);
    session.getMapper(SectorImportMapper.class).deleteByDataset(key);
    LOG.info("Delete dataset import history for dataset {}", key);
    session.getMapper(DatasetImportMapper.class).deleteByDataset(key);
    // delete data partitions
    Partitioner.delete(session, key);
    session.commit();
    // now also clear filesystem
    diDao.removeMetrics(key);
  }

  @Override
  protected void deleteAfter(Integer key, Dataset old, int user, DatasetMapper mapper, SqlSession session) {
    // drop managed id sequences
    if (DatasetInfoCache.CACHE.origin(key) == DatasetOrigin.MANAGED) {
      session.getMapper(DatasetPartitionMapper.class).deleteManagedSequences(key);
    }
    // clear search index asnychroneously
    CompletableFuture.supplyAsync(() -> indexService.deleteDataset(key))
            .exceptionally(e -> {
              LOG.error("Failed to delete ES docs for dataset {}", key, e.getCause());
              return 0;
            });
    // notify event bus
    bus.post(DatasetChanged.delete(key));
  }

  @Override
  protected void createAfter(Dataset obj, int user, DatasetMapper mapper, SqlSession session) {
    pullLogo(obj);
    if (obj.getOrigin() == DatasetOrigin.MANAGED) {
      recreatePartition(obj.getKey());
      Partitioner.createManagedSequences(factory, obj.getKey());
    }
    bus.post(DatasetChanged.change(obj));
    session.commit();
  }

  @Override
  protected void updateBefore(Dataset obj, Dataset old, int user, DatasetMapper mapper, SqlSession session) {
    super.updateBefore(obj, old, user, mapper, session);
  }

  @Override
  protected void updateAfter(Dataset obj, Dataset old, int user, DatasetMapper mapper, SqlSession session) {
    pullLogo(obj);
    if (obj.getOrigin() == DatasetOrigin.MANAGED && !session.getMapper(DatasetPartitionMapper.class).exists(obj.getKey())) {
      recreatePartition(obj.getKey());
    }
    bus.post(DatasetChanged.change(obj));
  }

  private void recreatePartition(int datasetKey) {
    Partitioner.partition(factory, datasetKey);
    Partitioner.indexAndAttach(factory, datasetKey);
  }

  private void pullLogo(Dataset d) {
    LogoUpdateJob.updateDatasetAsync(d, factory, downloader, scratchFileFunc, imgService);
  }

  public void addEditor(int key, int editorKey, User user) {
    changeEditor(key, editorKey, user, dm -> dm.addEditor(key, editorKey, user.getKey()));
  }

  public void removeEditor(int key, int editorKey, User user) {
    changeEditor(key, editorKey, user, dm -> dm.removeEditor(key, editorKey, user.getKey()));
  }

  private void changeEditor(int key, int editorKey, User user, Consumer<DatasetMapper> action) {
    if (!user.isAuthorized(key)) {
      throw new WebApplicationException(Response.Status.FORBIDDEN);
    }
    User editor;
    try (SqlSession session = factory.openSession()){
      editor = session.getMapper(UserMapper.class).get(editorKey);
      if (editor == null) {
        throw new IllegalArgumentException("Editor " + editorKey + " does not exist");
      }
      action.accept(session.getMapper(DatasetMapper.class));
      session.commit();
    }
    bus.post(new UserPermissionChanged(editor.getUsername()));
  }

}
