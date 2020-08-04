package life.catalogue.api.model;

import com.google.common.collect.EvictingQueue;

import java.util.Collection;
import java.util.Objects;
import java.util.Queue;

public class SectorImport extends ImportMetrics implements SectorEntity {

  private Integer sectorKey;
  private Integer ignoredUsageCount;
  
  private final Queue<String> warnings = EvictingQueue.create(25);
  
  public Collection<String> getWarnings() {
    return warnings;
  }
  
  public void setWarnings(Collection<String> warnings) {
    this.warnings.clear();
    this.warnings.addAll(warnings);
  }
  
  public void addWarning(String warning) {
    warnings.add(warning);
  }

  public Integer getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }
  
  public Integer getIgnoredUsageCount() {
    return ignoredUsageCount;
  }
  
  public void setIgnoredUsageCount(Integer ignoredUsageCount) {
    this.ignoredUsageCount = ignoredUsageCount;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SectorImport that = (SectorImport) o;
    return sectorKey == that.sectorKey &&
        Objects.equals(ignoredUsageCount, that.ignoredUsageCount) &&
        // EvictingQueue has no equals method implemented
        // for equality this hardly matters, so we just compare the sizes
        warnings.size() == that.warnings.size();
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, ignoredUsageCount, warnings);
  }
  
  @Override
  public String attempt() {
    return getSectorKey() + "#" + getAttempt();
  }

}
