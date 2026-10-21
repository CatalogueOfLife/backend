package life.catalogue.api.model;

/**
 * Detects records that state the same fact about a taxon, so a second one adds nothing and may even conflict.
 * Comparisons ignore timestamps, created/modifiedBy, dataset, sector and verbatim keys.
 * Most entities compare all their content, but some only their subject:
 * a distribution is a duplicate if it covers the same area, a taxon property if it has the same property.
 */
public interface DuplicateCheck<T> {
  boolean isDuplicate(T that);
}
