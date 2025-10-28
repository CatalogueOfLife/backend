package life.catalogue.dao;

import life.catalogue.api.event.DeleteSector;
import life.catalogue.api.model.*;
import life.catalogue.db.mapper.PublisherMapper;
import life.catalogue.db.mapper.SectorImportMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.event.EventBroker;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

public class PublisherDao extends DatasetEntityDao<UUID, Publisher, PublisherMapper> {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(PublisherDao.class);
  private final EventBroker bus;

  public PublisherDao(SqlSessionFactory factory, EventBroker bus, Validator validator) {
    super(false, factory, Publisher.class, PublisherMapper.class, validator);
    this.bus = bus;
  }

  @Override
  protected boolean deleteAfter(DSID<UUID> key, Publisher old, int user, PublisherMapper mapper, SqlSession session) {
    // also remove all merge sectors from datasets from this publisher
    final int projectKey = key.getDatasetKey();
    for (Sector s : session.getMapper(SectorMapper.class).listByDatasetPublisher(projectKey, key.getId())){
      bus.publish(new DeleteSector(DSID.of(projectKey, s.getId()), user));
    }
    return true;
  }

  @Override
  protected void validate(Publisher p) throws ConstraintViolationException {
    // require a UUID key!
    if (p.getId() == null || p.getDatasetKey() == null) {
      throw new IllegalArgumentException("ID and datasetKey are required");
    }
    super.validate(p);
  }

  /**
   * Returns aggregated metrics for all sectors of datasets published by the given publisher.
   * This methods works also for deleted releases as we keep metrics with the project.
   * @param datasetKey project or release key
   * @param id sector publisher UUID
   * @return aggregated metrics for all sectors of datasets published
   */
  public ImportMetrics sourceMetrics(int datasetKey, UUID id) {
    PublisherMetrics metrics = new PublisherMetrics(datasetKey, id);

    try (SqlSession session = factory.openSession()) {
      // could throw not found
      var info = DatasetInfoCache.CACHE.info(datasetKey, true);
      Integer projectKey;
      if (info.origin.isRelease()) {
        projectKey = info.sourceKey;
      } else {
        projectKey = datasetKey;
      }
      // aggregate metrics based on sector syncs/imports
      SectorImportMapper sim = session.getMapper(SectorImportMapper.class);
      AtomicInteger sectorCounter = new AtomicInteger(0);
      IntSet datasets = new IntOpenHashSet();
      // list all sectors from that publisher
      for (Sector s : session.getMapper(SectorMapper.class).listByDatasetPublisher(datasetKey, id)){
        if (s.getSyncAttempt() != null) {
          // retrieve metrics for that sync event and aggregate it
          SectorImport m = sim.get(DSID.of(projectKey, s.getId()), s.getSyncAttempt());
          metrics.add(m);
          sectorCounter.incrementAndGet();
          datasets.add(s.getSubjectDatasetKey());
        }
      }
      metrics.setSectorCount(sectorCounter.get());
      metrics.setDatasetCount(datasets.size());
    }
    return metrics;
  }

  static class PublisherMetrics extends ImportMetrics {
    private final UUID publisherKey;
    private int datasetCount;

    public PublisherMetrics(int datasetKey, UUID publisherKey) {
      setDatasetKey(datasetKey);
      this.publisherKey = publisherKey;
    }

    public UUID getPublisherKey() {
      return publisherKey;
    }

    public int getDatasetCount() {
      return datasetCount;
    }

    public void setDatasetCount(int datasetCount) {
      this.datasetCount = datasetCount;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      if (!super.equals(o)) return false;
      PublisherMetrics that = (PublisherMetrics) o;
      return datasetCount == that.datasetCount && Objects.equals(publisherKey, that.publisherKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), publisherKey, datasetCount);
    }
  }

}
