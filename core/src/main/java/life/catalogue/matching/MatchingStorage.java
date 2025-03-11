package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.cache.CacheLoader;
import life.catalogue.common.Managed;

import java.util.ArrayList;
import java.util.List;

public interface MatchingStorage extends Managed {

  NameUsageBase getUsage(DSID<String> src);

  List<SimpleNameCached> get(DSID<Integer> canonNidx);

  void put(DSID<Integer> canonNidx, List<SimpleNameCached> before);

  List<SimpleNameCached> getClassification(DSID<String> src);

  void invalidate(int datasetKey, int canonicalId);

  void invalidate(DSID<Integer> key);

  /**
   * Removes a single entry from the matcher cache.
   * If it is not cached yet, nothing will happen.
   * @param nidx any names index id
   */
  void clear(int datasetKey, int nidx);

  /**
   * Removes all usages from the given dataset from the matcher cache.
   */
  void clear(int datasetKey);

  /**
   * Wipes the entire cache.
   */
  void clear();

  /**
   * @param datasetKey
   * @param usage with parent property being the ID !!!
   * @param loader
   * @return
   */
  default SimpleNameClassified<SimpleNameCached> withClassification(int datasetKey, SimpleNameCached usage) throws NotFoundException {
    SimpleNameClassified<SimpleNameCached> sncl = new SimpleNameClassified<>(usage);
    sncl.setClassification(new ArrayList<>());
    //if (usage.getParent() != null) {
    //  addParents(sncl.getClassification(), DSID.of(datasetKey, usage.getParent()), loader);
    //}
    return sncl;
  }

}
