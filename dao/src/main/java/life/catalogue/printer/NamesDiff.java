package life.catalogue.printer;

import life.catalogue.printer.diff.ChangedName;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Structured diff between two sorted name lists.
 * label1/label2 identify the two sides (e.g. "dataset_3#1" for an import attempt or "dataset_3" for a dataset key).
 * removed = present in side 1 but not side 2; added = present in side 2 but not side 1;
 * changed = pairs of removed/added names that are similar enough to be treated as a modification.
 */
public class NamesDiff {
  private final String label1;
  private final String label2;
  private final List<String> removed = new ArrayList<>();
  private final List<String> added = new ArrayList<>();
  private final List<ChangedName> changed = new ArrayList<>();
  private boolean truncated = false;

  @JsonCreator
  public NamesDiff(@JsonProperty("label1") String label1, @JsonProperty("label2") String label2) {
    this.label1 = label1;
    this.label2 = label2;
  }

  @JsonProperty("label1")
  public String getLabel1() { return label1; }
  @JsonProperty("label2")
  public String getLabel2() { return label2; }

  public List<String> getRemoved() { return removed; }
  public List<String> getAdded() { return added; }
  public List<ChangedName> getChanged() { return changed; }

  public int getRemovedCount() { return removed.size(); }
  public int getAddedCount() { return added.size(); }
  public int getChangedCount() { return changed.size(); }

  public boolean isTruncated() { return truncated; }
  public void setTruncated(boolean truncated) { this.truncated = truncated; }

  public boolean isIdentical() {
    return removed.isEmpty() && added.isEmpty() && changed.isEmpty();
  }

  @Override
  public String toString() {
    return "NamesDiff{" + label1 + " vs " + label2 +
      ", removed=" + removed.size() + ", added=" + added.size() + ", changed=" + changed.size() +
      (truncated ? ", truncated" : "") + '}';
  }
}
