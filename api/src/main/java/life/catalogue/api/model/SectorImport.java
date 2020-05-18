package life.catalogue.api.model;

import com.google.common.base.Predicates;
import com.google.common.collect.EvictingQueue;

import java.util.*;
import java.util.stream.Collectors;

public class SectorImport extends ImportMetrics<SectorImport.State> {
  
  public enum State {
    WAITING, PREPARING, COPYING, DELETING, RELINKING, INDEXING, FINISHED, CANCELED, FAILED;
    
    public boolean isRunning() {
      return this != FINISHED && this != FAILED && this != CANCELED;
    }
  }

  private int sectorKey;
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

  public int getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(int sectorKey) {
    this.sectorKey = sectorKey;
  }
  
  public Integer getIgnoredUsageCount() {
    return ignoredUsageCount;
  }
  
  public void setIgnoredUsageCount(Integer ignoredUsageCount) {
    this.ignoredUsageCount = ignoredUsageCount;
  }
  
  public static List<State> runningStates() {
    return Arrays.stream(State.values())
        .filter(State::isRunning)
        .collect(Collectors.toList());
  }
  
  public static List<State> finishedStates() {
    return Arrays.stream(State.values())
        .filter(Predicates.not(State::isRunning))
        .collect(Collectors.toList());
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
