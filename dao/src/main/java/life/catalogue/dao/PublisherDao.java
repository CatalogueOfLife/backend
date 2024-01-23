package life.catalogue.dao;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SectorSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.License;
import life.catalogue.db.SectorProcessable;
import life.catalogue.db.mapper.*;
import life.catalogue.es.NameUsageIndexService;

import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.validation.ConstraintViolationException;
import javax.validation.Validator;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PublisherDao extends DatasetEntityDao<UUID, Publisher, PublisherMapper> {
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(PublisherDao.class);

  public PublisherDao(SqlSessionFactory factory, Validator validator) {
    super(false, factory, Publisher.class, PublisherMapper.class, validator);
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
   * Returns aggregated metrics for all sectors of datasets published by the given publisher
   * @param datasetKey project or release key
   * @param id sector publisher UUID
   * @return aggregated metrics for all sectors of datasets published
   */
  public ImportMetrics sourceMetrics(int datasetKey, UUID id) {
    PublisherMetrics metrics = new PublisherMetrics(datasetKey, id);

    try (SqlSession session = factory.openSession()) {
      // could throw not found
      var info = DatasetInfoCache.CACHE.info(datasetKey);
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
