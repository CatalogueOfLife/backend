package org.col.api.model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.google.common.base.Predicates;

public class SectorImport extends ImportMetrics<SectorImport.State> {
  
  public enum State {
    WAITING, PREPARING, COPYING, DELETING, RELINKING, INDEXING, FINISHED, CANCELED, FAILED;
    
    public boolean isRunning() {
      return this != FINISHED && this != FAILED && this != CANCELED;
    }
  }

  private int sectorKey;
  
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
    return sectorKey == that.sectorKey;
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey);
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
    return getSectorKey() + " - " + getAttempt();
  }
  
}
