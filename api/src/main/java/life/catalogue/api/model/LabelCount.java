package life.catalogue.api.model;

import java.util.Objects;

/**
 * A single, rendered name usage label (scientific name plus authorship, if any) together with the
 * number of name_match rows sharing that label for a given names index entry.
 * Used by {@link life.catalogue.db.mapper.NameMatchMapper#labelCounts(int)} to summarise which
 * distinct source labels a single canonical nidx bucket aggregates.
 */
public class LabelCount {
  private String label;
  private int count;

  public LabelCount() {
  }

  public LabelCount(String label, int count) {
    this.label = label;
    this.count = count;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LabelCount)) return false;
    LabelCount that = (LabelCount) o;
    return count == that.count && Objects.equals(label, that.label);
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, count);
  }

  @Override
  public String toString() {
    return "LabelCount{" + label + "=" + count + '}';
  }
}
