package life.catalogue.api.model;

import java.util.function.Function;

import static life.catalogue.api.vocab.Datasets.DRAFT_COL;

/**
 * DatasetScopedID: Entity with an ID property scoped within a single dataset.
 */
public interface DSID<K> extends DatasetScoped {
  
  K getId();
  
  void setId(K id);

  /**
   * Builder style for fluent tests but reusing the same instance
   */
  default DSID<K> id(K id) {
    setId(id);
    return this;
  }

  /**
   * Returns a colon concatenated version of both dataset key and id
   */
  default String concat() {
    return getDatasetKey() + ":" + getId();
  }

  /**
   * Parses a colon concatenated version of both dataset key and id
   */
  static <K> DSID<K> parse(String key, Function<String, K> parser) {
    if (key == null) return null;
    String[] parts = key.split(":", 2);
    if (parts.length == 1) return null;
    int datasetKey = Integer.parseInt(parts[0]);
    return of(datasetKey, parser.apply(parts[1]));
  }

  static DSID<Integer> parseInt(String key) {
    return parse(key, Integer::parseInt);
  }

  static DSID<String> parseStr(String key) {
    return parse(key, x -> x);
  }

  /**
   * @return a dataset scoped id using the verbatimKey of the supplied src
   */
  static DSIDValue<Integer> vkey(VerbatimEntity src) {
    return new DSIDValue<Integer>(src.getDatasetKey(), src.getVerbatimKey());
  }

  static <K> DSIDValue<K> copy(DSID<K> src) {
    return new DSIDValue<K>(src.getDatasetKey(), src.getId());
  }

  static <K> DSIDValue<K> of(int datasetKey, K id) {
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
