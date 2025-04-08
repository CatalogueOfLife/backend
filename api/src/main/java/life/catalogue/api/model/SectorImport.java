package life.catalogue.api.model;

import life.catalogue.api.vocab.InfoGroup;

import java.util.*;

public class SectorImport extends ImportMetrics implements SectorScoped {
  private static final int MAX_WARN_SIZE = 25;

  private Integer sectorKey;
  private Sector.Mode sectorMode;
  private Integer datasetAttempt;
  private Map<InfoGroup, Integer> secondarySourceByInfoCount = new HashMap<>();

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

  @Override
  public Sector.Mode getSectorMode() {
    return sectorMode;
  }

  @Override
  public void setSectorMode(Sector.Mode sectorMode) {
    this.sectorMode = sectorMode;
  }

  public Integer getDatasetAttempt() {
    return datasetAttempt;
  }

  public void setDatasetAttempt(Integer datasetAttempt) {
    this.datasetAttempt = datasetAttempt;
  }

  public Map<InfoGroup, Integer> getSecondarySourceByInfoCount() {
    return secondarySourceByInfoCount;
  }

  public void setSecondarySourceByInfoCount(Map<InfoGroup, Integer> secondarySourceByInfoCount) {
    this.secondarySourceByInfoCount = secondarySourceByInfoCount;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SectorImport that = (SectorImport) o;
    return Objects.equals(sectorKey, that.sectorKey) && sectorMode == that.sectorMode && Objects.equals(datasetAttempt, that.datasetAttempt) && Objects.equals(secondarySourceByInfoCount, that.secondarySourceByInfoCount) && Objects.equals(warnings, that.warnings);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, sectorMode, datasetAttempt, secondarySourceByInfoCount, warnings);
  }

  @Override
  public String attempt() {
    return getSectorKey() + "#" + getAttempt();
  }

}
