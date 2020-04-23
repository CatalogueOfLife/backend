package life.catalogue.dao;

import com.google.common.eventbus.EventBus;
import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Frequency;
import life.catalogue.common.io.DownloadUtil;
import life.catalogue.db.DatasetPageable;
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
import java.io.File;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

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
    final DatasetSearchRequest req = nullableRequest == null || nullableRequest.isEmpty() ? new DatasetSearchRequest() : nullableRequest;
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
    // remove decisions, sectors and estimates
    for (Class<DatasetPageable<?>> mapperCLass : new Class[]{SectorMapper.class, DecisionMapper.class, EstimateMapper.class}) {
      session.getMapper(mapperCLass).deleteByDataset(key);
    }
    // delete data partitions
    Partitioner.delete(session, key);
    session.commit();
    // now also clear filesystem
    diDao.removeMetrics(key);
  }

  @Override
  protected void deleteAfter(Integer key, Dataset old, int user, DatasetMapper mapper, SqlSession session) {
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
  public Integer create(Dataset obj, int user) {
    // make sure the creator is an editor
    obj.addEditor(user);
    return super.create(resetOriginProps(obj), user);
  }

  private static Dataset resetOriginProps(Dataset d) {
    // null properties not matching its origin
    if (d.getOrigin() != null) {
      switch (d.getOrigin()) {
        case MANAGED:
        case RELEASED:
          d.setDataFormat(null);
          d.setDataAccess(null);
          d.setImportFrequency(Frequency.NEVER);
      }
    }
    return d;
  }

  @Override
  protected void createAfter(Dataset obj, int user, DatasetMapper mapper, SqlSession session) {
    pullLogo(obj);
    if (obj.getOrigin() == DatasetOrigin.MANAGED) {
      recreatePartition(obj.getKey());
    }
    bus.post(DatasetChanged.change(obj));
    session.commit();
  }

  @Override
  protected void updateBefore(Dataset obj, Dataset old, int user, DatasetMapper mapper, SqlSession session) {
    // make sure to never remove the creator from editors
    if (obj.getCreatedBy() != null) {
      obj.addEditor(obj.getCreatedBy());
    }
    super.updateBefore(resetOriginProps(obj), old, user, mapper, session);
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

}
