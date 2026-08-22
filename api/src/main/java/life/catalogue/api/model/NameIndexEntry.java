package life.catalogue.api.model;

import life.catalogue.common.tax.NameFormatter;

import java.util.Objects;

/**
 * Minimal mutable carrier for a single {@code names_index} row: a canonical name bucketed by its
 * normalized key.
 *
 * The names index itself is a bare {@code normalized-String -> nidx-int} registry (see
 * {@link life.catalogue.matching.nidx.NameIndexStore}); this class exists solely as a MyBatis
 * carrier for {@link life.catalogue.db.mapper.NamesIndexMapper} to move a row's scientificName,
 * normalized bucket key and generated primary key in and out of postgres. It carries nothing else -
 * no rank, authorship or atomized epithets.
 */
public class NameIndexEntry implements Entity<Integer> {
  private Integer key;
  private String scientificName;
  private String normalized;

  public NameIndexEntry() {
  }

  @Override
  public Integer getKey() {
    return key;
  }

  @Override
  public void setKey(Integer key) {
    this.key = key;
  }

  public String getScientificName() {
    return scientificName;
  }

  public void setScientificName(String scientificName) {
    this.scientificName = scientificName;
  }

  /**
   * The normalized bucket key for this canonical name (folded, gender-stemmed, lower-cased, ASCII-only).
   */
  public String getNormalized() {
    return normalized;
  }

  public void setNormalized(String normalized) {
    this.normalized = normalized;
  }

  /**
   * Builds a fresh canonical carrier ready for {@link life.catalogue.db.mapper.NamesIndexMapper#createOnConflict(NameIndexEntry)}:
   * the scientific name is the canonical name of the given source, the normalized bucket key is the
   * already-computed key handed in by the matcher.
   */
  public static NameIndexEntry canonical(FormattableName n, String normalized) {
    NameIndexEntry e = new NameIndexEntry();
    e.setScientificName(NameFormatter.canonicalName(n));
    e.setNormalized(normalized);
    return e;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NameIndexEntry)) return false;
    NameIndexEntry that = (NameIndexEntry) o;
    return Objects.equals(key, that.key) &&
      Objects.equals(scientificName, that.scientificName) &&
      Objects.equals(normalized, that.normalized);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, scientificName, normalized);
  }

  @Override
  public String toString() {
    return key + " " + scientificName;
  }
}
