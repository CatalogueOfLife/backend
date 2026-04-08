package life.catalogue.api.model;

import life.catalogue.api.vocab.TaxGroup;
import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Hierarchical breakdown of a dataset by taxonomic groups with counts of accepted taxa by a given rank, usually species.
 */
public class DatasetBreakdown {
  private int datasetKey;
  private Rank countBy;
  private List<GroupBreakdown> breakdown;

  public int getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(int datasetKey) {
    this.datasetKey = datasetKey;
  }

  public Rank getCountBy() {
    return countBy;
  }

  public void setCountBy(Rank countBy) {
    this.countBy = countBy;
  }

  public List<GroupBreakdown> getBreakdown() {
    return breakdown;
  }

  public void setBreakdown(List<GroupBreakdown> breakdown) {
    this.breakdown = breakdown;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DatasetBreakdown that)) return false;

    return datasetKey == that.datasetKey &&
        countBy == that.countBy &&
        Objects.equals(breakdown, that.breakdown);
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, countBy, breakdown);
  }

  public static class GroupBreakdown {
    private TaxGroup group;
    private int count;
    private List<GroupBreakdown> breakdown;

    public GroupBreakdown(TaxGroup group) {
      this.group = group;
      this.breakdown = new ArrayList<>();
    }

    public TaxGroup getGroup() {
      return group;
    }

    public void setGroup(TaxGroup group) {
      this.group = group;
    }

    public int getCount() {
      return count;
    }

    public void setCount(int count) {
      this.count = count;
    }

    public List<GroupBreakdown> getBreakdown() {
      return breakdown;
    }

    public void setBreakdown(List<GroupBreakdown> breakdown) {
      this.breakdown = breakdown;
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof GroupBreakdown that)) return false;

      return count == that.count &&
          group == that.group &&
          Objects.equals(breakdown, that.breakdown);
    }

    @Override
    public int hashCode() {
      return Objects.hash(group, count, breakdown);
    }
  }

}

