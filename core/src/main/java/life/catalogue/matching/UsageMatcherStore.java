package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;

import life.catalogue.matching.nidx.NameIndex;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public interface UsageMatcherStore extends AutoCloseable {
  Logger LOG = LoggerFactory.getLogger(UsageMatcherStore.class);

  /**
   * Maximum number of usages indexed per canonical names index id.
   *
   * <p>A canonical name shared by more usages than this cannot discriminate anything. Environmental
   * bacterial datasets are the extreme case - canonicalisation strips strain and clone identifiers, so
   * "? bacterium" collects tens of thousands of usages. Every one of them would be returned by
   * {@link #usagesByCanonicalId(int)} as a match candidate, each needing its own classification walk,
   * only for the authorship and rank comparison to fail to pick a winner among them.
   *
   * <p>Capping bounds that work and keeps the chronicle store below its hard per-entry ceiling: a
   * ChronicleMap entry may not exceed one segment tier, so an uncapped list eventually fails the whole
   * build with "Value too large: entry takes n chunks". Usages beyond the cap are not offered as match
   * candidates for that canonical.
   */
  int MAX_USAGES_PER_CANONICAL = 5000;

  /**
   * Loads the dataset into the matcher store, using the previously persisted name matches.
   * @param factory
   */
  default int load(SqlSessionFactory factory){
    LOG.info("Start loading all usages from dataset {}", datasetKey());
    var cnt = new AtomicInteger();
    try (SqlSession session = factory.openSession()) {
      var num = session.getMapper(NameUsageMapper.class);
      PgUtils.consume(() -> num.processDatasetSimpleNidx(datasetKey()), sn -> {
        add(sn);
        cnt.incrementAndGet();
      });
    }
    LOG.info("Loaded {} usages for dataset {}", cnt, datasetKey());
    return cnt.intValue();
  }

  /**
   * Loads the dataset into the matcher store, using an explicit names index to (re)match all usages against
   * before they are added. Writes to the names index are allowed.
   * @param factory
   * @param ni
   */
  default int load(SqlSessionFactory factory, NameIndex ni){
    LOG.info("Start loading all usages from dataset {}", datasetKey());
    var cnt = new AtomicInteger();
    try (SqlSession session = factory.openSession()) {
      var num = session.getMapper(NameUsageMapper.class);
      PgUtils.consume(() -> num.processDataset(datasetKey()), u -> matchAndAdd(u, ni));
    }
    LOG.info("Loaded {} usages for dataset {}", cnt, datasetKey());
    return cnt.intValue();
  }

  private void matchAndAdd(NameUsageBase u, NameIndex ni) {
    var m = ni.match(u.getName(), true, false);
    u.getName().applyMatch(m);
    var sn = new SimpleNameCached(u, m.getNidx());
    add(sn);
  }

  default int analyze(TaxGroupAnalyzer analyzer){
    int noCounter = 0;
    LOG.info("Analyze tax groups for all usages for dataset {}", datasetKey());
    for (var u : all()) {
      var cl = getClassification(u.getId());
      var tg = analyzer.analyze(u, cl);
      update(u.getId(), tg);
    }
    LOG.info("Tax groups analyzed for dataset {} with {} usages having no group", datasetKey(), noCounter);
    return noCounter;
  }

  int datasetKey();

  int size();

  /**
   * @return number of distinct canonical name index ids held in the inverted canonical index
   */
  default int canonicalSize() {
    int cnt = 0;
    for (var id : allCanonicalIds()) {
      cnt++;
    }
    return cnt;
  }

  default boolean isEmpty() {
    return size() < 1;
  }

  /**
   * @param canonId a canonical names index id
   * @return list of matching usages that act as candidates for the match
   */
  List<SimpleNameClassified<SimpleNameCached>> usagesByCanonicalId(int canonId);

  List<SimpleNameCached> simpleNamesByCanonicalId(int canonId);

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

  void update(String usageID, TaxGroup group);

  Iterable<SimpleNameCached> all();

  Iterable<Integer> allCanonicalIds();

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
   * Moves the taxon given to a new parent by updating the parent_id
   * @param usageID the taxon to update
   * @param parentId the new parentId to assign
   */
  void updateParentId(String usageID, String parentId);

  @Override
  void close();

}
