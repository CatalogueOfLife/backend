package life.catalogue.matching.decision;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.dao.DaoUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.BaseDecisionMapper;

import java.util.Objects;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

public abstract class RematcherBase<
    T extends DatasetScopedEntity<Integer>,
    R extends RematchRequest,
    S,
    M extends BaseDecisionMapper<T, S>
  > {
  private static final Logger LOG = LoggerFactory.getLogger(RematcherBase.class);

  private final String type;
  private final Class<M> mapperClass;
  private MatchingDao mdao;
  private SqlSessionFactory factory;
  SqlSession session;
  M mapper;
  final int projectKey;
  final int userKey;
  final R req;

  MatchCounter counter = new MatchCounter();

  public RematcherBase(Class<T> type, Class<M> mapperClass, R req, int userKey, SqlSessionFactory factory) {
    this.userKey = userKey;
    this.req = req;
    this.type = type.getSimpleName();
    this.mapperClass = mapperClass;
    this.factory = factory;
    // check if dataset key is given and has data partitions
    Preconditions.checkArgument(req.getDatasetKey() != null, "DatasetKey required for rematching");
    this.projectKey = req.getDatasetKey();
    try(SqlSession session = factory.openSession(true)) {
      var info = DatasetInfoCache.CACHE.info(projectKey);
    }
  }

  abstract S toSearchRequest(R req);

  T exists(DSID<Integer> key, T obj) {
    if (obj == null) {
      throw new NotFoundException(type + " " + key.getId() + " does not exist in project " + projectKey);
    }
    return obj;
  }

  public static class MatchCounter {
    private int broken;
    private int updated;
    private int unchanged;
  
    public int getBroken() {
      return broken;
    }
  
    public int getUpdated() {
      return updated;
    }
  
    public int getUnchanged() {
      return unchanged;
    }
  
    public int getTotal() {
      return updated + unchanged;
    }

    @Override
    public String toString() {
      return "broken=" + broken +
        ", updated=" + updated +
        ", unchanged=" + unchanged;
    }
  }

  MatchCounter match(){
    try(SqlSession session = factory.openSession(true)) {
      this.session = session;
      if (!req.isAllowImmutableDatasets()) {
        DaoUtils.assertMutable(projectKey, "matched", session);
      }
      mdao = new MatchingDao(session);
      mapper = session.getMapper(mapperClass);
      if (req.getId() != null){
        T ed = exists(req, mapper.get(req));
        LOG.info("Match {} {} from project {}", type, req.getId(), projectKey);
        match(ed);

      } else {
        LOG.info("Match {}s from project {}", type, projectKey);
        S searchRequest = toSearchRequest(req);
        PgUtils.consume(() -> mapper.processSearch(searchRequest), this::match);
      }
      LOG.info("Rematched {} {}s from project {}. updated: {}, broken: {}", counter.getTotal(), type, projectKey, counter.updated, counter.broken);
      return counter;
    }
  }
  
  abstract void match(T obj);

  /**
   * @return true if the simple name id was changed
   */
  boolean updateCounter(boolean hasSubject, String idBefore, String idAfter) {
    return updateCounter(hasSubject, idBefore, idAfter, false, null, null);
  }

  /**
   * @return true if the simple name id was changed
   */
  boolean updateCounter(boolean hasSubject, String sIdBefore, String sIdAfter, boolean hasTarget, String tIdBefore, String tIdAfter) {
    boolean changed = !Objects.equals(sIdBefore, sIdAfter) || !Objects.equals(tIdBefore, tIdAfter);
    if (changed) {
      counter.updated++;
    } else {
      counter.unchanged++;
    }
    // broken separately from (un)changed
    if (hasSubject && sIdAfter == null || hasTarget && tIdAfter == null) {
      counter.broken++;
    }
    return changed;
  }

  NameUsage matchSubjectUniquely(int datasetKey, DatasetScopedEntity<Integer> obj, SimpleName sn, String originalId){
    return matchUniquely(datasetKey, obj, sn, originalId);
  }

  NameUsage matchTargetUniquely(DatasetScopedEntity<Integer> obj, SimpleName sn){
    return matchUniquely(projectKey, obj, sn, null);
  }

  /**
   * Tries to strictly and uniquely match a simple name instance to a given dataset.
   * If multiple matches exist it tries to select the previously matched usage, if that does not exist the originally assigned usage.
   * If both do not exist and we have multiple matches do not match to any of them and return null.
   *
   * @param obj the object (sector,decision) containing the SimpleName sn
   * @param sn the name (sector,decision) to match
   * @param datasetKey the dataset to match the names against
   * @return the unique match or null
   */
  private NameUsage matchUniquely(int datasetKey, DatasetScopedEntity<Integer> obj, SimpleName sn, String originalId){
    var result = mdao.matchDataset(sn, datasetKey);
    LOG.info("Match {} {} from project {} to dataset {}: {}", obj.getClass().getSimpleName(), obj.getKey(), projectKey, datasetKey, result);

    if (result.isEmpty()) {
      LOG.warn("{} {} cannot be rematched - lost {}.", obj.getClass().getSimpleName(), obj.getKey(), sn);
    } else if (result.size() > 1) {
      // keep the existing id if it still matches!
      if (sn.getId() != null) {
        for (NameUsage nu : result.getMatches()){
          if (nu.getId().equals(sn.getId())) {
            LOG.info("{} {} matches multiple usages in dataset {} - existing usage {} still matching",
              obj.getClass().getSimpleName(), obj.getKey(), datasetKey, sn);
            return nu;
          }
        }
      }
      // if we haven't found the previous id, try with the original id
      if (originalId != null) {
        for (NameUsage nu : result.getMatches()){
          if (nu.getId().equals(originalId)) {
            LOG.info("{} {} matches multiple usages in dataset {} - original usage ID {} still matching to {}",
              obj.getClass().getSimpleName(), obj.getKey(), datasetKey, originalId, nu.getLabel());
            return nu;
          }
        }
      }
      LOG.warn("{} {} cannot be uniquely rematched to dataset {} - multiple names like {}",
        obj.getClass().getSimpleName(), obj.getKey(), datasetKey, sn);
    } else {
      return result.getMatches().get(0);
    }
    return null;
  }
  
}
