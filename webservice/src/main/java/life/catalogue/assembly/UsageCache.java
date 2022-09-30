package life.catalogue.assembly;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleNameClassified;

import life.catalogue.api.model.SimpleNameWithPub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Simple KVP style cache for name usages in the form of SimpleNameWithPub instances.
 */
public interface UsageCache {
  Logger LOG = LoggerFactory.getLogger(UsageCache.class);


  boolean contains(DSID<String> key);

  SimpleNameWithPub get(DSID<String> key);

  SimpleNameWithPub put(int datasetKey, SimpleNameWithPub usage);

  SimpleNameWithPub remove(DSID<String> key);

  void clear(int datasetKey);

  void clear();

  /**
   * @param datasetKey
   * @param usage with parent property being the ID !!!
   * @param loader
   * @return
   */
  default SimpleNameClassified withClassification(int datasetKey, SimpleNameWithPub usage, Function<DSID<String>, SimpleNameWithPub> loader) {
    SimpleNameClassified sncl = new SimpleNameClassified(usage);
    sncl.setClassification(new ArrayList<>());
    if (usage.getParent() != null) {
      addParents(sncl.getClassification(), DSID.of(datasetKey, usage.getParent()), loader);
    }
    return sncl;
  }

  private void addParents(List<SimpleNameWithPub> classification, DSID<String> parentKey, Function<DSID<String>, SimpleNameWithPub> loader) {
    SimpleNameWithPub p;
    if (contains(parentKey)) {
      p = get(parentKey);
    } else {
      p = loader.apply(parentKey);
      if (p != null) {
        put(parentKey.getDatasetKey(), p);
      } else {
        LOG.warn("Missing usage {}", parentKey);
      }
    }
    if (p != null) {
      classification.add(p);
      if (p.getParent() != null) {
        addParents(classification, parentKey.id(p.getParent()), loader);
      }
    }
  }

  /**
   * A usage cache that does nothing and keeps nothing in memory
   */
  static UsageCache passThru() {
    return new UsageCache() {
      @Override
      public boolean contains(DSID<String> key) {
        return false;
      }

      @Override
      public SimpleNameWithPub get(DSID<String> key) {
        return null;
      }

      @Override
      public SimpleNameWithPub put(int datasetKey, SimpleNameWithPub usage) {
        return null;
      }

      @Override
      public SimpleNameWithPub remove(DSID<String> key) {
        return null;
      }

      @Override
      public void clear(int datasetKey) { }

      @Override
      public void clear() { }
    };
  }

  /**
   * A simple cache backed by an in memory hash map that grows forever.
   * Really only for tests...
   */
  static UsageCache hashMap() {
    return new UsageCache() {
      private final Map<DSID<String>, SimpleNameWithPub> data = new HashMap<>();

      @Override
      public boolean contains(DSID<String> key) {
        return data.containsKey(key);
      }

      @Override
      public SimpleNameWithPub get(DSID<String> key) {
        return data.get(key);
      }

      @Override
      public SimpleNameWithPub put(int datasetKey, SimpleNameWithPub usage) {
        return data.put(DSID.of(datasetKey, usage.getId()), usage);
      }

      @Override
      public SimpleNameWithPub remove(DSID<String> key) {
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
    };
  }
}
