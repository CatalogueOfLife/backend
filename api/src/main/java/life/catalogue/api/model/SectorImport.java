package life.catalogue.api.model;

import java.util.*;

import com.google.common.collect.EvictingQueue;

public class SectorImport extends ImportMetrics implements SectorEntity {
  private static final int MAX_WARN_SIZE = 25;

  private Integer sectorKey;
  private Integer datasetAttempt;

  private final List<String> warnings = new ArrayList<>();

  public Collection<String> getWarnings() {
    return warnings;
  }
  
  public void setWarnings(Collection<String> warnings) {
    this.warnings.clear();
    this.warnings.addAll(warnings);
    // keep list to max
    if (this.warnings.size() > MAX_WARN_SIZE) {
      this.warnings.subList(MAX_WARN_SIZE, this.warnings.size()).clear();
    }
  }
  
  public void addWarning(String warning) {
    if (warnings.size() < MAX_WARN_SIZE) {
      warnings.add(warning);
    }
  }

  public Integer getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }

  public Integer getDatasetAttempt() {
    return datasetAttempt;
  }

  public void setDatasetAttempt(Integer datasetAttempt) {
    this.datasetAttempt = datasetAttempt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SectorImport)) return false;
    if (!super.equals(o)) return false;
    SectorImport that = (SectorImport) o;
    return Objects.equals(sectorKey, that.sectorKey)
           && Objects.equals(datasetAttempt, that.datasetAttempt)
           && Objects.equals(warnings, that.warnings);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, datasetAttempt, warnings);
  }

  @Override
  public String attempt() {
    return getSectorKey() + "#" + getAttempt();
  }

}
