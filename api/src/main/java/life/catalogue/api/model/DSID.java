package life.catalogue.api.model;

import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

import static life.catalogue.api.vocab.Datasets.COL;

/**
 * DatasetScopedID: Entity with an ID property scoped within a single dataset.
 */
public interface DSID<K> extends DatasetScoped, Entity<DSID<K>>, HasID<K> {
  Pattern keyPattern = Pattern.compile("^(-?\\d+):(.+)$");
  Pattern keyIntPattern = Pattern.compile("^(-?\\d+):(\\d+)$");

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
   * @return true when the DSID has an ID value
   */
  default boolean hasId() {
    return getId() == null;
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

  static DSIDValue<String> ofStr(String key) {
    if (StringUtils.isBlank(key)) return null;
    var m = keyPattern.matcher(key);
    if (m.find()) {
      // NumberFormatException cannot happen as we have a pattern
      return new DSIDValue<>(Integer.parseInt(m.group(1)), m.group(2));
    } else {
      throw new IllegalArgumentException("DSID key not prefixed by dataset key");
    }
  }

  static DSIDValue<Integer> ofInt(String key) {
    if (StringUtils.isBlank(key)) return null;
    var m = keyIntPattern.matcher(key);
    if (m.find()) {
      // NumberFormatException cannot happen as we have a pattern
      return new DSIDValue<>(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)));
    } else {
      throw new IllegalArgumentException("DSID key not prefixed by dataset key");
    }
  }

  /**
   * A DSID with just a datasetKey and null as the id.
   * Used for querying root taxa.
   */
  static <K> DSIDValue<K> root(int datasetKey) {
    return new DSIDValue<K>(datasetKey, null);
  }

  static <K> DSIDValue<K> colID(K id) {
    return new DSIDValue<K>(COL, id);
  }

}
