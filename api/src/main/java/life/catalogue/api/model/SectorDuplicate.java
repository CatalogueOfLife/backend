package life.catalogue.api.model;

import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

/**
 * A group of sectors in one project or release that share the same subject and are therefore potential duplicates.
 * Subject less sectors from the same source dataset share their missing subject and form a group too.
 */
public class SectorDuplicate {
  private int datasetKey;
  private int subjectDatasetKey;
  @Nullable
  private String subjectId;
  private List<Sector> sectors;

  /**
   * A group as it comes out of the database, with only the sector keys ordered by priority.
   */
  public static class Mybatis {
    private int datasetKey;
    private int subjectDatasetKey;
    private String subjectId;
    private int[] keys;

    public int getDatasetKey() {
      return datasetKey;
    }

    public void setDatasetKey(int datasetKey) {
      this.datasetKey = datasetKey;
    }

    public int getSubjectDatasetKey() {
      return subjectDatasetKey;
    }

    public void setSubjectDatasetKey(int subjectDatasetKey) {
      this.subjectDatasetKey = subjectDatasetKey;
    }

    public String getSubjectId() {
      return subjectId;
    }

    public void setSubjectId(String subjectId) {
      this.subjectId = subjectId;
    }

    public int[] getKeys() {
      return keys;
    }

    public void setKeys(int[] keys) {
      this.keys = keys;
    }
  }

  public SectorDuplicate() {
  }

  public SectorDuplicate(int datasetKey, int subjectDatasetKey, @Nullable String subjectId, List<Sector> sectors) {
    this.datasetKey = datasetKey;
    this.subjectDatasetKey = subjectDatasetKey;
    this.subjectId = subjectId;
    this.sectors = sectors;
  }

  public int getDatasetKey() {
    return datasetKey;
  }

  public void setDatasetKey(int datasetKey) {
    this.datasetKey = datasetKey;
  }

  public int getSubjectDatasetKey() {
    return subjectDatasetKey;
  }

  public void setSubjectDatasetKey(int subjectDatasetKey) {
    this.subjectDatasetKey = subjectDatasetKey;
  }

  @Nullable
  public String getSubjectId() {
    return subjectId;
  }

  public void setSubjectId(@Nullable String subjectId) {
    this.subjectId = subjectId;
  }

  /**
   * @return the sectors of the group ordered by priority and key
   */
  public List<Sector> getSectors() {
    return sectors;
  }

  public void setSectors(List<Sector> sectors) {
    this.sectors = sectors;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    SectorDuplicate that = (SectorDuplicate) o;
    return datasetKey == that.datasetKey && subjectDatasetKey == that.subjectDatasetKey && Objects.equals(subjectId, that.subjectId) && Objects.equals(sectors, that.sectors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, subjectDatasetKey, subjectId, sectors);
  }
}
