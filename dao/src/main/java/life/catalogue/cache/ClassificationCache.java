package life.catalogue.cache;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple KVP style loadable cache for name usages from a fixed dataset
 * in the form of SimpleNameWithPub instances.
 */
public interface ClassificationCache extends AutoCloseable {
  Logger LOG = LoggerFactory.getLogger(ClassificationCache.class);

  boolean contains(String key);

  SimpleNameCached get(String key);

  void put(SimpleNameCached usage);

  /**
   * Removes the usage with the given key from the storage
   */
  void clear(String key);

  @Override
  void close();

  /**
   * @param usage with parent property being the ID !!!
   * @return
   */
  default SimpleNameClassified<SimpleNameCached> withClassification(SimpleNameCached usage) throws NotFoundException {
    SimpleNameClassified<SimpleNameCached> sncl = new SimpleNameClassified<>(usage);
    sncl.setClassification(getClassification(usage.getParentId()));
    return sncl;
  }

  /**
   * @return entire classification including the start ID
   * @throws NotFoundException if the start ID or any subsequent parentID cannot be resolved
   */
  default List<SimpleNameCached> getClassification(String start) throws NotFoundException {
    List<SimpleNameCached> classification = new ArrayList<>();
    String pid = start;
    while (pid != null) {
      var p = get(pid);
      classification.add(p);
      pid = p.getParentId();
    }
    return classification;
  }

}
