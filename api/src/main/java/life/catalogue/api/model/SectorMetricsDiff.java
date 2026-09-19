package life.catalogue.api.model;

import java.util.Objects;

import javax.annotation.Nullable;

import org.gbif.nameparser.api.Rank;

/**
 * The comparison of one sector between two datasets - usually a release and the previous release of the same kind.
 * Only sectors worth looking at during a release review are rendered, see {@link Flag}.
 *
 * Sector ids are stable across releases because SectorMapper.copyDataset keeps them, so the join is on the
 * sector id alone.
 */
public class SectorMetricsDiff {

  public enum Flag {
    /**
     * The sector contributed usages before and contributes none now - almost always a broken sync or a source
     * that failed to import, and the single most important thing a release review has to catch.
     */
    ZERO,

    /**
     * The sector grew by more than the requested relative threshold.
     */
    INCREASED,

    /**
     * The sector shrank by more than the requested relative threshold, but still has usages.
     */
    DECREASED,

    /**
     * The sector does not exist in the dataset compared against.
     */
    NEW,

    /**
     * The sector existed in the dataset compared against, but no longer exists here.
     */
    REMOVED
  }

  private int sectorKey;
  private Sector.Mode mode;
  private Integer subjectDatasetKey;
  private String subjectName;
  private String targetName;
  private Rank targetRank;
  private Integer attempt;
  private Integer prevAttempt;
  private int usagesCount;
  private int prevUsagesCount;
  private Integer taxonCount;
  private Integer synonymCount;
  /**
   * The relative change of the usage count, i.e. (current-previous)/previous. -1 means everything was lost.
   * Null when there is nothing to divide by, i.e. the sector had no usages before (NEW, or a sector that was
   * empty and now has content).
   */
  private Double change;
  private Flag flag;

  public int getSectorKey() {
    return sectorKey;
  }

  public void setSectorKey(int sectorKey) {
    this.sectorKey = sectorKey;
  }

  public Sector.Mode getMode() {
    return mode;
  }

  public void setMode(Sector.Mode mode) {
    this.mode = mode;
  }

  public Integer getSubjectDatasetKey() {
    return subjectDatasetKey;
  }

  public void setSubjectDatasetKey(Integer subjectDatasetKey) {
    this.subjectDatasetKey = subjectDatasetKey;
  }

  public String getSubjectName() {
    return subjectName;
  }

  public void setSubjectName(String subjectName) {
    this.subjectName = subjectName;
  }

  public String getTargetName() {
    return targetName;
  }

  public void setTargetName(String targetName) {
    this.targetName = targetName;
  }

  public Rank getTargetRank() {
    return targetRank;
  }

  public void setTargetRank(Rank targetRank) {
    this.targetRank = targetRank;
  }

  public Integer getAttempt() {
    return attempt;
  }

  public void setAttempt(Integer attempt) {
    this.attempt = attempt;
  }

  public Integer getPrevAttempt() {
    return prevAttempt;
  }

  public void setPrevAttempt(Integer prevAttempt) {
    this.prevAttempt = prevAttempt;
  }

  public int getUsagesCount() {
    return usagesCount;
  }

  public void setUsagesCount(int usagesCount) {
    this.usagesCount = usagesCount;
  }

  public int getPrevUsagesCount() {
    return prevUsagesCount;
  }

  public void setPrevUsagesCount(int prevUsagesCount) {
    this.prevUsagesCount = prevUsagesCount;
  }

  public Integer getTaxonCount() {
    return taxonCount;
  }

  public void setTaxonCount(Integer taxonCount) {
    this.taxonCount = taxonCount;
  }

  public Integer getSynonymCount() {
    return synonymCount;
  }

  public void setSynonymCount(Integer synonymCount) {
    this.synonymCount = synonymCount;
  }

  public @Nullable Double getChange() {
    return change;
  }

  public void setChange(Double change) {
    this.change = change;
  }

  public Flag getFlag() {
    return flag;
  }

  public void setFlag(Flag flag) {
    this.flag = flag;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SectorMetricsDiff)) return false;
    SectorMetricsDiff that = (SectorMetricsDiff) o;
    return sectorKey == that.sectorKey
      && usagesCount == that.usagesCount
      && prevUsagesCount == that.prevUsagesCount
      && mode == that.mode
      && Objects.equals(subjectDatasetKey, that.subjectDatasetKey)
      && Objects.equals(subjectName, that.subjectName)
      && Objects.equals(targetName, that.targetName)
      && targetRank == that.targetRank
      && Objects.equals(attempt, that.attempt)
      && Objects.equals(prevAttempt, that.prevAttempt)
      && Objects.equals(taxonCount, that.taxonCount)
      && Objects.equals(synonymCount, that.synonymCount)
      && Objects.equals(change, that.change)
      && flag == that.flag;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sectorKey, mode, subjectDatasetKey, subjectName, targetName, targetRank, attempt, prevAttempt,
      usagesCount, prevUsagesCount, taxonCount, synonymCount, change, flag);
  }

  @Override
  public String toString() {
    return "SectorMetricsDiff{" + sectorKey + " " + flag + " " + prevUsagesCount + "->" + usagesCount + '}';
  }
}
