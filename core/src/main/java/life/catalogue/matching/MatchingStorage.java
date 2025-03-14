package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageSearchRequest;
import life.catalogue.api.search.NameUsageSearchResponse;
import life.catalogue.common.Managed;
import life.catalogue.es.NameUsageSearchService;

import java.util.Collections;
import java.util.List;

/**
 * Storage needed for matching service against a specific target dataset
 * @param <T>
 */
public interface MatchingStorage<T extends SimpleNameWithNidx> {

  /**
   * @return the dataset key the storage belongs to
   */
  int datasetKey();

  List<T> get(int canonNidx);

  void put(int canonNidx, List<T> usages);

  /**
   * @return entire classification including the start usageKey
   * @param usageKey
   * @throws NotFoundException if the start usageKey or any subsequent parentID cannot be resolved
   */
  List<T> getClassification(String usageKey);

  /**
   * Creates a new simple name instance, but does not store it yet.
   * @param nu the source usage to convert
   * @param canonNidx the canonical names index match to include with the usage instance
   */
  T convert(NameUsageBase nu, int canonNidx);

  void clear(int canonNidx);

  default SimpleNameClassified<T> withClassification(T usage) throws NotFoundException {
    SimpleNameClassified<T> sncl = new SimpleNameClassified<>(usage);
    sncl.setClassification(getClassification(usage.getId()));
    return sncl;
  }
}
