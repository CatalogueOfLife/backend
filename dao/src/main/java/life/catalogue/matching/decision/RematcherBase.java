package life.catalogue.matching.decision;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.SimpleName;
import life.catalogue.dao.DaoUtils;
import life.catalogue.db.mapper.BaseDecisionMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

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
  SqlSession session;
  M mapper;
  final int projectKey;
  final int userKey;
  final R req;

  MatchCounter counter = new MatchCounter();

  public RematcherBase(Class<T> type, Class<M> mapperClass, R req, int userKey) {
    this.userKey = userKey;
    this.req = req;
    this.projectKey = req.getDatasetKey();
    this.type = type.getSimpleName();
    this.mapperClass = mapperClass;
  }

  abstract S toSearchRequest(R req);

  T verify(DSID<Integer> key, T obj) {
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
      return broken + updated + unchanged;
    }
  }

  MatchCounter match(SqlSessionFactory factory){
    try(SqlSession session = factory.openSession(true)) {
      this.session = session;
      DaoUtils.assertMutable(projectKey, "matched", session);
      mdao = new MatchingDao(session);
      mapper = session.getMapper(mapperClass);
      if (req.getId() != null){
        T ed = verify(req, mapper.get(req));
        LOG.info("Match {} {} from project {}", type, req.getId(), projectKey);
        match(ed);

      } else {
        LOG.info("Match {}s from project {}", type, projectKey);
        S searchRequest = toSearchRequest(req);
        mapper.processSearch(searchRequest).forEach(this::match);
      }
      LOG.info("Rematched {} {}s from project {}. updated: {}, broken: {}", counter.getTotal(), type, projectKey, counter.updated, counter.broken);
      return counter;
    }
  }
  
  abstract void match(T obj);

  /**
   * @return true if the simple name id was changed
   */
  boolean updateCounter(String idBefore, String idAfter) {
    boolean changed = !Objects.equals(idBefore, idAfter);
    if (idAfter == null) {
      counter.broken++;
    } else if (changed) {
      counter.updated++;
    } else {
      counter.unchanged++;
    }
    return changed;
  }

  /**
   * @return true if the simple name id was changed
   */
  boolean updateCounter(String sIdBefore, String sIdAfter, String tIdBefore, String tIdAfter) {
    boolean changed = !Objects.equals(sIdBefore, sIdAfter) || !Objects.equals(tIdBefore, tIdAfter);
    if (sIdAfter == null || tIdAfter == null) {
      counter.broken++;
    } else if (changed) {
      counter.updated++;
    } else {
      counter.unchanged++;
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
    List<? extends NameUsage> matches = mdao.matchDataset(sn, datasetKey);
    if (matches.isEmpty()) {
      LOG.warn("{} {} from project {} cannot be rematched to dataset {} - lost {}",
        obj.getClass().getSimpleName(), obj.getKey(), projectKey, datasetKey, sn);
    } else if (matches.size() > 1) {
      // keep the existing id if it still matches!
      if (sn.getId() != null) {
        for (NameUsage nu : matches){
          if (nu.getId().equals(sn.getId())) {
            LOG.info("{} {} from project {} matches multiple usages in dataset {} - existing usage {} still matching",
              obj.getClass().getSimpleName(), obj.getKey(), projectKey, datasetKey, sn);
            return nu;
          }
        }
      }
      // if we havent found the previous id, try with the original id
      if (originalId != null) {
        for (NameUsage nu : matches){
          if (nu.getId().equals(originalId)) {
            LOG.info("{} {} from project {} matches multiple usages in dataset {} - original usage ID {} still matching to {}",
              obj.getClass().getSimpleName(), obj.getKey(), projectKey, datasetKey, originalId, nu.getLabel());
            return nu;
          }
        }
      }
      LOG.warn("{} {} from project {} cannot be uniquely rematched to dataset {} - multiple names like {}",
        obj.getClass().getSimpleName(), obj.getKey(), projectKey, datasetKey, sn);
    } else {
      return matches.get(0);
    }
    return null;
  }
  
}
