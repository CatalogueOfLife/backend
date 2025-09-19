package life.catalogue.cache;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple KVP style cache for name usages in the form of SimpleNameWithPub instances.
 */
public interface UsageCacheSingleDS extends AutoCloseable {
  Logger LOG = LoggerFactory.getLogger(UsageCacheSingleDS.class);


  boolean contains(String key);

  SimpleNameCached get(String key);

  SimpleNameCached put(SimpleNameCached usage);

  SimpleNameCached remove(String key);

  @Override
  void close();

  /**
   * @param usage with parent property being the ID !!!
   * @return
   */
  default SimpleNameClassified<SimpleNameCached> withClassification(SimpleNameCached usage) throws NotFoundException {
    SimpleNameClassified<SimpleNameCached> sncl = new SimpleNameClassified<>(usage);
    sncl.setClassification(new ArrayList<>());
    if (usage.getParent() != null) {
      addParents(sncl.getClassification(), usage.getParent());
    }
    return sncl;
  }

  default SimpleNameClassified<SimpleNameCached> getSimpleNameClassified(String id) throws NotFoundException {
    var snc = new SimpleNameClassified<SimpleNameCached>(get(id));
    return withClassification(snc);
  }

  /**
   * @return entire classification including the start ID
   * @throws NotFoundException if the start ID or any subsequent parentID cannot be resolved
   */
  default List<SimpleNameCached> getClassification(String start) throws NotFoundException {
    List<SimpleNameCached> classification = new ArrayList<>();
    addParents(classification, start);
    return classification;
  }

  private void addParents(List<SimpleNameCached> classification, String parentKey) throws NotFoundException {
    addParents(classification, parentKey, new HashSet<>());
  }

  private void addParents(List<SimpleNameCached> classification, String parentKey, Set<String> visitedIDs) throws NotFoundException {
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

  /**
   * Updates the parentID of the cached names belonging to the given datasetKey
   * and having the given oldParentID.
   * @param oldParentID
   * @param newParentID
   */
  void updateParent(String oldParentID, String newParentID);

  /**
   * A simple cache backed by an in memory hash map that grows forever.
   */
  static UsageCacheSingleDS hashMap() {
    return new UsageCacheSingleDS() {

      private final Map<String, SimpleNameCached> data = new HashMap<>();

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
      public void close() { }

      @Override
      public void updateParent(String oldParentID, String newParentID) {
        int count = 0;
        for (var sn : data.values()) {
          if (Objects.equals(oldParentID, sn.getParent())) {
            sn.setParent(newParentID);
            count++;
          }
        }
        LOG.debug("Updated {} usages with new parentID {} in the cache", count, newParentID);
      }
    };
  }
}
