package life.catalogue.cache;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple KVP style cache for name usages from a single dataset in the form of SimpleNameWithPub instances.
 */
public interface UsageCache extends AutoCloseable {
  Logger LOG = LoggerFactory.getLogger(UsageCache.class);

  int getDatasetKey();

  boolean contains(String key);

  SimpleNameCached get(String key);

  SimpleNameCached put(SimpleNameCached usage);

  SimpleNameCached remove(String key);

  void clear();

  @Override
  void close();

  default SimpleNameCached getOrLoad(String key, CacheLoader loader) {
    var sn = get(key);
    if (sn == null) {
      sn = loader.load(key);
      if (sn == null) {
        // first try to commit and see if we can get it then
        loader.commit();
        sn = loader.load(key);
      }
      if (sn != null) {
        put(sn);
      } else {
        LOG.warn("Missing usage {}", key);
      }
    }
    return sn;
  }

  /**
   * Loads the entire dataset from postgres into the cache.
   * @return number of loaded usages
   */
  default int load(SqlSessionFactory factory) {
    LOG.info("Load all usages of dataset {} into cache", getDatasetKey());
    AtomicInteger cnt = new AtomicInteger();
    try (SqlSession session = factory.openSession()) {
      PgUtils.consume(() -> session.getMapper(NameUsageMapper.class).processDatasetSimpleNidx(getDatasetKey()), u -> {
        put(u);
        cnt.incrementAndGet();
      });
    }
    LOG.info("Loaded {} usages of dataset {} into cache", cnt.get(), getDatasetKey());
    return cnt.intValue();
  }

  /**
   * @param usage with parent property being the ID !!!
   * @param loader
   * @return
   */
  default SimpleNameClassified<SimpleNameCached> withClassification(SimpleNameCached usage, CacheLoader loader) throws NotFoundException {
    SimpleNameClassified<SimpleNameCached> sncl = new SimpleNameClassified<>(usage);
    sncl.setClassification(new ArrayList<>());
    if (usage.getParent() != null) {
      addParents(sncl.getClassification(), usage.getParent(), loader);
    }
    return sncl;
  }

  /**
   * @return entire classification including the start ID
   * @throws NotFoundException if the start ID or any subsequent parentID cannot be resolved
   */
  default List<SimpleNameCached> getClassification(String start, CacheLoader loader) throws NotFoundException {
    List<SimpleNameCached> classification = new ArrayList<>();
    addParents(classification, start, loader);
    return classification;
  }

  private void addParents(List<SimpleNameCached> classification, String parentKey, CacheLoader loader) throws NotFoundException {
    addParents(classification, parentKey, loader, new HashSet<>());
  }

  private void addParents(List<SimpleNameCached> classification, String parentKey, CacheLoader loader, Set<String> visitedIDs) throws NotFoundException {
    SimpleNameCached p;
    if (contains(parentKey)) {
      p = get(parentKey);
    } else {
      p = loader.load(parentKey);
      if (p == null) {
        // first try to commit and see if we can get it then
        loader.commit();
        p = loader.load(parentKey);
      }
      if (p != null) {
        put(p);
      } else {
        LOG.warn("Missing usage {}", parentKey);
        throw NotFoundException.notFound(NameUsage.class, parentKey);
      }
    }
    if (p != null) {
      visitedIDs.add(parentKey);
      classification.add(p);
      if (p.getParent() != null) {
        if (visitedIDs.contains(p.getParent())) {
          LOG.warn("Bad classification tree with parent circles involving {}", p);
        } else {
          addParents(classification, p.getParent(), loader, visitedIDs);
        }
      }
    }
  }

  static UsageCache mapDB(int datasetKey, File location) {
    return new UsageCacheMapDB(datasetKey, location, 8);
  }

  /**
   * A usage cache that does nothing and keeps nothing in memory
   */
  static UsageCache passThru() {
    return new UsageCache() {
      @Override
      public int getDatasetKey() {
        return -1;
      }

      @Override
      public boolean contains(String key) {
        return false;
      }

      @Override
      public SimpleNameCached get(String key) {
        return null;
      }

      @Override
      public SimpleNameCached put(SimpleNameCached usage) {
        return null;
      }

      @Override
      public SimpleNameCached remove(String key) {
        return null;
      }

      @Override
      public void clear() { }

      @Override
      public void close() { }
    };
  }

  /**
   * A simple cache backed by an in memory hash map that grows forever.
   */
  static UsageCache hashMap(final int datasetKey) {
    return new UsageCache() {
      private final int dKey = datasetKey;
      private final Map<String, SimpleNameCached> data = new HashMap<>();

      @Override
      public int getDatasetKey() {
        return dKey;
      }

      @Override
      public boolean contains(String key) {
        return data.containsKey(key);
      }

      @Override
      public SimpleNameCached get(String key) {
        return data.get(key);
      }

      @Override
      public SimpleNameCached put(SimpleNameCached usage) {
        return data.put(usage.getId(), usage);
      }

      @Override
      public SimpleNameCached remove(String key) {
        return data.remove(key);
      }

      @Override
      public void clear() {
        data.clear();
      }

      @Override
      public void close() { }
    };
  }
}
