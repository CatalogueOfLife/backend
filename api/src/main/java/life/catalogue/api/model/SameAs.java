package life.catalogue.api.model;

/**
 * Content comparison of entity instances only which ignore the timestamps, created/modifiedBy, dataset, sector or verbatim keys.
 */
public interface SameAs<T> {
  boolean sameAs(T that);
}
