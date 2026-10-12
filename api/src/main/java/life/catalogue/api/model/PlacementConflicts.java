package life.catalogue.api.model;

import life.catalogue.api.vocab.DatasetType;

import java.util.Objects;

/**
 * The share of a dataset's accepted genera flagged with a PLACEMENT_CONFLICT in its latest import,
 * i.e. placed in a taxonomic group the reference checklist does not know them in.
 * See https://github.com/CatalogueOfLife/backend/issues/1608
 */
public class PlacementConflicts {
  private Integer datasetKey;
  private String title;
  private DatasetType type;
  private Integer attempt;
  private int genera;
  private int conflicts;
  private double ratio;

  public Integer getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public DatasetType getType() {
    return type;
  }

  public void setType(DatasetType type) {
    this.type = type;
  }

  /**
   * @return the import attempt the counts are taken from
   */
  public Integer getAttempt() {
    return attempt;
  }

  public void setAttempt(Integer attempt) {
    this.attempt = attempt;
  }

  /**
   * @return number of accepted genera in the dataset
   */
  public int getGenera() {
    return genera;
  }

  public void setGenera(int genera) {
    this.genera = genera;
  }

  /**
   * @return number of accepted genera flagged with a placement conflict
   */
  public int getConflicts() {
    return conflicts;
  }

  public void setConflicts(int conflicts) {
    this.conflicts = conflicts;
  }

  /**
   * @return conflicts divided by genera
   */
  public double getRatio() {
    return ratio;
  }

  public void setRatio(double ratio) {
    this.ratio = ratio;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PlacementConflicts that)) return false;
    return genera == that.genera && conflicts == that.conflicts && Double.compare(ratio, that.ratio) == 0
      && Objects.equals(datasetKey, that.datasetKey) && Objects.equals(title, that.title) && type == that.type
      && Objects.equals(attempt, that.attempt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, title, type, attempt, genera, conflicts, ratio);
  }

  @Override
  public String toString() {
    return datasetKey + " " + conflicts + "/" + genera + " " + title;
  }
}
