package org.col.api.model;

import java.util.*;
import java.util.stream.Collectors;

import com.google.common.base.Predicates;
import com.google.common.collect.EvictingQueue;

public class SectorImport extends ImportMetrics<SectorImport.State> {
  
  public enum State {
    WAITING, PREPARING, COPYING, DELETING, RELINKING, INDEXING, FINISHED, CANCELED, FAILED;
    
    public boolean isRunning() {
      return this != FINISHED && this != FAILED && this != CANCELED;
    }
  }
  
  private String type;
  private int sectorKey;
  private final Queue<String> warnings = EvictingQueue.create(10);
  
  public Collection<String> getWarnings() {
    return warnings;
  }
  
  public void setWarnings(Collection<String> warnings) {
    this.warnings.addAll(warnings);
  }
  
  public void addWarning(String warning) {
    warnings.add(warning);
  }
  
  public String getType() {
    return type;
  }
  
  public void setType(String type) {
    this.type = type;
  }
  
  public int getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(int sectorKey) {
    this.sectorKey = sectorKey;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    SectorImport that = (SectorImport) o;
    // we dont include warnings as we dont persist them
    return sectorKey == that.sectorKey &&
        Objects.equals(type, that.type);
  }
  
  @Override
  public int hashCode() {
    // we dont include warnings as we dont persist them
    return Objects.hash(super.hashCode(), type, sectorKey);
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
  public String attempt() {
    return getType() + " " + getSectorKey() + " - " + getAttempt();
  }
  
}
