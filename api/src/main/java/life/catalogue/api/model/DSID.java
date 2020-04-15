package life.catalogue.api.model;

import static life.catalogue.api.vocab.Datasets.DRAFT_COL;

/**
 * DatasetScopedID: Entity with an ID property scoped within a single dataset.
 */
public interface DSID<K> extends DatasetScoped {
  
  K getId();
  
  void setId(K id);
  
  
  /**
   * @return a dataset scoped id using the verbatimKey of the supplied src
   */
  static DSIDValue<Integer> vkey(VerbatimEntity src) {
    return new DSIDValue<Integer>(src.getDatasetKey(), src.getVerbatimKey());
  }

  static <K> DSIDValue<K> copy(DSID<K> src) {
    return new DSIDValue<K>(src.getDatasetKey(), src.getId());
  }

  static <K> DSIDValue<K> key(int datasetKey, K id) {
    return new DSIDValue<K>(datasetKey, id);
  }

  /**
   * Creates a DSID with a fixed datasetKey of -1 that should not mean anything.
   * Can be used for globally unique ids like we use in decisions.
   */
  static <K> DSIDValue<K> idOnly(K id) {
    return new DSIDValue<K>(-1, id);
  }

  static <K> DSIDValue<K> draftID(K id) {
    return new DSIDValue<K>(DRAFT_COL, id);
  }
  
}
