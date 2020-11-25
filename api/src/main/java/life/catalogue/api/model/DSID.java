package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.Objects;
import java.util.function.Function;

import static life.catalogue.api.vocab.Datasets.COL;

/**
 * DatasetScopedID: Entity with an ID property scoped within a single dataset.
 */
public interface DSID<K> extends DatasetScoped, Entity<DSID<K>> {
  
  K getId();
  
  void setId(K id);

  /**
   * Builder style for fluent tests but reusing the same instance
   */
  default DSID<K> id(K id) {
    setId(id);
    return this;
  }

  @JsonIgnore
  default DSID<K> getKey() {
    return DSID.of(getDatasetKey(), getId());
  }

  @JsonIgnore
  default void setKey(DSID<K> key) {
    if (key == null) {
      setDatasetKey(null);
      setId(null);
    } else {
      setDatasetKey(key.getDatasetKey());
      setId(key.getId());
    }
  }

  /**
   * Returns a colon concatenated version of both dataset key and id
   */
  default String concat() {
    return getDatasetKey() + ":" + getId();
  }

  /**
   * Checks whether 2 instances of DSID potentially using different implementation classes are identical
   * when it comes to their DSID content.
   * @param d1
   * @param d2
   * @return true if both are equal identifiers
   */
  static <K> boolean equals(DSID<K> d1, DSID<K> d2) {
    return Objects.equals(d1.getDatasetKey(), d2.getDatasetKey())
        && Objects.equals(d1.getId(), d2.getId());
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

  static <K> DSIDValue<K> draftID(K id) {
    return new DSIDValue<K>(COL, id);
  }

}
