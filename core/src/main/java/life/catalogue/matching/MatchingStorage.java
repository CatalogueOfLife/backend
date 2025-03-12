package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.common.Managed;

import java.util.List;

public interface MatchingStorage<T extends SimpleNameWithNidx> extends Managed {

  List<T> get(DSID<Integer> canonNidx);

  void put(DSID<Integer> canonNidx, List<T> usages);

  List<T> getClassification(DSID<String> usage);

  /**
   * Creates a new simple name instance, but does not store it yet.
   * @param nu the source usage to convert
   * @param canonNidx the canonical names index match to include with the usage instance
   */
  T convert(NameUsageBase nu, DSID<Integer> canonNidx);

  void clear(DSID<Integer> canonNidx);

  /**
   * Removes all usages from the given dataset.
   */
  void clear(int datasetKey);

  /**
   * Wipes the entire storage.
   */
  void clear();

  default SimpleNameClassified<T> withClassification(int datasetKey, T usage) throws NotFoundException {
    SimpleNameClassified<T> sncl = new SimpleNameClassified<>(usage);
    sncl.setClassification(getClassification(DSID.of(datasetKey, usage.getId())));
    return sncl;
  }

}
