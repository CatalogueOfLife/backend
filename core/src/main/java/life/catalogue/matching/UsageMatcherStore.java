package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public interface UsageMatcherStore extends AutoCloseable {
  Logger LOG = LoggerFactory.getLogger(UsageMatcherStore.class);

  default int load(int datasetKey, SqlSessionFactory factory){
    var cnt = new AtomicInteger();
    try (SqlSession session = factory.openSession()) {
      var num = session.getMapper(NameUsageMapper.class);
      PgUtils.consume(() -> num.processDatasetSimpleNidx(datasetKey), sn -> {
        add(sn);
        cnt.incrementAndGet();
      });
    }
    LOG.info("Loaded {} usages for dataset {}", cnt, datasetKey);
    return cnt.intValue();
  }

  int size();

  /**
   * @param nidx a canonical names index id
   * @return list of matching usages that act as candidates for the match
   */
  List<SimpleNameClassified<SimpleNameCached>> usagesByCanonicalNidx(int nidx);

  /**
   * @param usageID the id to start retrieving the classification from
   * @return classification including and starting with the given usageID
   * @throws NotFoundException
   */
  default List<SimpleNameCached> getClassification(String usageID) throws NotFoundException {
    List<SimpleNameCached> classification = new ArrayList<>();
    addParents(classification, usageID, new HashSet<>());
    return classification;
  }

  private void addParents(List<SimpleNameCached> classification, String parentKey, Set<String> visitedIDs) throws NotFoundException {
    if (parentKey != null) {
      SimpleNameCached p = get(parentKey);
      if (p == null) {
        LOG.warn("Missing usage {}", parentKey);
        throw NotFoundException.notFound(NameUsage.class, parentKey);
      }
      visitedIDs.add(parentKey);
      classification.add(p);
      if (p.getParent() != null) {
        if (visitedIDs.contains(p.getParent())) {
          throw new IllegalStateException("Bad classification tree with parent circles involving " + p);
        } else {
          addParents(classification, p.getParent(), visitedIDs);
        }
      }
    }
  }

  SimpleNameCached get(String usageID) throws NotFoundException;

  default SimpleNameClassified<SimpleNameCached> getSNClassified(String id) throws NotFoundException {
    var snc = new SimpleNameClassified<SimpleNameCached>(get(id));
    snc.setClassification(getClassification(snc.getParentId()));
    return snc;
  }

  /**
   * Adds a new name to the cache.
   * If the same id already exists it should behave like an update
   * @param sn
   */
  void add(SimpleNameCached sn);

  /**
   * Change an existing usage ID
   * @param oldID
   * @param newID
   */
  void updateUsageID(String oldID, String newID);

  /**
   * Moves the taxon given to a new parent by updating the parent_id
   * @param usageID the taxon to update
   * @param parentId the new parentId to assign
   */
  void updateParentId(String usageID, String parentId);

  @Override
  void close();

}
