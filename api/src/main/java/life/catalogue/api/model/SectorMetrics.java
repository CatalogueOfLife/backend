package life.catalogue.api.model;

import java.util.Objects;

import org.gbif.nameparser.api.Rank;

/**
 * A compact projection of a sector together with the metrics of the sync attempt the sector currently points at.
 *
 * Deliberately not a {@link SectorImport}: a release review compares thousands of sectors at once - COLs base
 * release has ~600 sectors, the extended release ~22.000 - and the full import metrics carry some 60 columns each,
 * more than half of them hstore maps. This is the handful of numbers a review actually reads.
 *
 * The metrics always live under the PROJECT key while the sector row may belong to a release, which is why
 * they are selected with two separate dataset keys. See SectorImportMapper.listMetrics.
 */
public class SectorMetrics {
  private int sectorKey;
  private Sector.Mode mode;
  private Integer subjectDatasetKey;
  private String subjectName;
  private String targetName;
  private Rank targetRank;
  /**
   * The sync attempt the sector points at, or null when the sector was never synced or its metrics have been
   * pruned by the retention job. A null attempt means the counts below are unknown, not zero.
   */
  private Integer attempt;
  private Integer taxonCount;
  private Integer synonymCount;
  /**
   * All usages regardless of their status, i.e. the SQL equivalent of {@link ImportMetrics#getUsagesCount()}.
   */
  private int usagesCount;

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

  public int getUsagesCount() {
    return usagesCount;
  }

  public void setUsagesCount(int usagesCount) {
    this.usagesCount = usagesCount;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SectorMetrics)) return false;
    SectorMetrics that = (SectorMetrics) o;
    return sectorKey == that.sectorKey
      && usagesCount == that.usagesCount
      && mode == that.mode
      && Objects.equals(subjectDatasetKey, that.subjectDatasetKey)
      && Objects.equals(subjectName, that.subjectName)
      && Objects.equals(targetName, that.targetName)
      && targetRank == that.targetRank
      && Objects.equals(attempt, that.attempt)
      && Objects.equals(taxonCount, that.taxonCount)
      && Objects.equals(synonymCount, that.synonymCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(sectorKey, mode, subjectDatasetKey, subjectName, targetName, targetRank, attempt, taxonCount, synonymCount, usagesCount);
  }

  @Override
  public String toString() {
    return "SectorMetrics{" + sectorKey + " " + mode + " attempt=" + attempt + " usages=" + usagesCount + '}';
  }
}
