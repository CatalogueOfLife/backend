package life.catalogue.cache;

import com.google.common.eventbus.Subscribe;

import life.catalogue.api.event.DatasetChanged;
import life.catalogue.api.event.DatasetDataChanged;
import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.model.SimpleNameCached;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import life.catalogue.common.Managed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple KVP style cache for name usages in the form of SimpleNameWithPub instances.
 */
public interface UsageCache extends AutoCloseable, Managed {
  Logger LOG = LoggerFactory.getLogger(UsageCache.class);


  boolean contains(DSID<String> key);

  SimpleNameCached get(DSID<String> key);

  SimpleNameCached put(int datasetKey, SimpleNameCached usage);

  SimpleNameCached remove(DSID<String> key);

  void clear(int datasetKey);

  void clear();

  @Override
  void close();

  @Subscribe
  default void datasetChanged(DatasetChanged event){
    if (event.isDeletion()) {
      clear(event.key);
    }
  }

  @Subscribe
  default void dataChanged(DatasetDataChanged event){
    clear(event.datasetKey);
  }

  default SimpleNameCached getOrLoad(DSID<String> key, Function<DSID<String>, SimpleNameCached> loader) {
    var sn = get(key);
    if (sn == null) {
      sn = loader.apply(key);
      if (sn != null) {
        put(key.getDatasetKey(), sn);
      } else {
        LOG.warn("Missing usage {}", key);
      }
    }
    return sn;
  }

  /**
   * @param datasetKey
   * @param usage with parent property being the ID !!!
   * @param loader
   * @return
   */
  default SimpleNameClassified<SimpleNameCached> withClassification(int datasetKey, SimpleNameCached usage, Function<DSID<String>, SimpleNameCached> loader) throws NotFoundException {
    SimpleNameClassified<SimpleNameCached> sncl = new SimpleNameClassified<>(usage);
    sncl.setClassification(new ArrayList<>());
    if (usage.getParent() != null) {
      addParents(sncl.getClassification(), DSID.of(datasetKey, usage.getParent()), loader);
    }
    return sncl;
  }

  default List<SimpleNameCached> getClassification(DSID<String> start, Function<DSID<String>, SimpleNameCached> loader) throws NotFoundException {
    List<SimpleNameCached> classification = new ArrayList<>();
    addParents(classification, start, loader);
    return classification;
  }

  private void addParents(List<SimpleNameCached> classification, DSID<String> parentKey, Function<DSID<String>, SimpleNameCached> loader) throws NotFoundException {
    addParents(classification, parentKey, loader, new HashSet<>());
  }

  private void addParents(List<SimpleNameCached> classification, DSID<String> parentKey, Function<DSID<String>, SimpleNameCached> loader, Set<String> visitedIDs) throws NotFoundException {
    SimpleNameCached p;
    if (contains(parentKey)) {
      p = get(parentKey);
    } else {
      p = loader.apply(parentKey);
      if (p != null) {
        put(parentKey.getDatasetKey(), p);
      } else {
        LOG.warn("Missing usage {}", parentKey);
        throw NotFoundException.notFound(NameUsage.class, parentKey);
      }
    }
    if (p != null) {
      visitedIDs.add(parentKey.getId());
      classification.add(p);
      if (p.getParent() != null) {
        if (visitedIDs.contains(p.getParent())) {
          LOG.warn("Bad classification tree with parent circles involving {}", p);
        } else {
          addParents(classification, parentKey.id(p.getParent()), loader);
        }
      }
    }
  }

  static UsageCache mapDB(File location, boolean expireMutable, boolean deleteOnClose, int kryoMaxCapacity) throws IOException {
    return new UsageCacheMapDB(location, expireMutable, deleteOnClose, kryoMaxCapacity);
  }

  /**
   * A usage cache that does nothing and keeps nothing in memory
   */
  static UsageCache passThru() {
    return new UsageCache() {
      @Override
      public void start() throws Exception {      }

      @Override
      public void stop() throws Exception {      }

      @Override
      public boolean hasStarted() {
        return true;
      }

      @Override
      public boolean contains(DSID<String> key) {
        return false;
      }

      @Override
      public SimpleNameCached get(DSID<String> key) {
        return null;
      }

      @Override
      public SimpleNameCached put(int datasetKey, SimpleNameCached usage) {
        return null;
      }

      @Override
      public SimpleNameCached remove(DSID<String> key) {
        return null;
      }

      @Override
      public void clear(int datasetKey) { }

      @Override
      public void clear() { }

      @Override
      public void close() { }
    };
  }

  /**
   * A simple cache backed by an in memory hash map that grows forever.
   * Really only for tests...
   */
  static UsageCache hashMap() {
    return new UsageCache() {
      @Override
      public void start() throws Exception {      }

      @Override
      public void stop() throws Exception {      }

      private final Map<DSID<String>, SimpleNameCached> data = new HashMap<>();

      @Override
      public boolean contains(DSID<String> key) {
        return data.containsKey(key);
      }

      @Override
      public SimpleNameCached get(DSID<String> key) {
        return data.get(key);
      }

      @Override
      public SimpleNameCached put(int datasetKey, SimpleNameCached usage) {
        return data.put(DSID.of(datasetKey, usage.getId()), usage);
      }

      @Override
      public SimpleNameCached remove(DSID<String> key) {
        return data.remove(key);
      }

      @Override
      public void clear(int datasetKey) {
        int count = 0;
        for (var k : data.keySet()) {
          if (datasetKey == k.getDatasetKey()) {
            data.remove(k);
            count++;
          }
        }
        LOG.info("Cleared all {} usages for datasetKey {} from the cache", count, datasetKey);
      }

      @Override
      public void clear() {
        data.clear();
      }

      @Override
      public void close() { }

      @Override
      public boolean hasStarted() {
        return true;
      }
    };
  }
}
