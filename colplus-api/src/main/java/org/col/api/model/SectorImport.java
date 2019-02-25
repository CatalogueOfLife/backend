package org.col.api.model;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import com.google.common.base.Predicates;
import org.col.api.vocab.Issue;

public class SectorImport {
  public enum State {
    WAITING, PREPARING, COPYING, DELETING, RELINKING, INDEXING, FINISHED, CANCELED, FAILED;
    
    public boolean isRunning() {
      return this != FINISHED && this != FAILED && this != CANCELED;
    }
  }

  private int sectorKey;
  private int attempt;
  private State state;
  
  /**
   * Time the import started
   */
  private LocalDateTime started;
  
  /**
   * Time the import finished
   */
  private LocalDateTime finished;
  private String error;
  
  // data for diffs
  private String textTree;
  private Set<String> names;
  // metrics
  private Integer descriptionCount;
  private Integer distributionCount;
  private Integer mediaCount;
  private Integer nameCount;
  private Integer referenceCount;
  private Integer taxonCount;
  private Integer vernacularCount;
  private Map<Issue, Integer> issueCount;
  private StatusRankCounts usagesByRankCount = new StatusRankCounts();
  
  public int getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(int sectorKey) {
    this.sectorKey = sectorKey;
  }
  
  public int getAttempt() {
    return attempt;
  }
  
  public void setAttempt(int attempt) {
    this.attempt = attempt;
  }
  
  public State getState() {
    return state;
  }
  
  public void setState(State state) {
    this.state = state;
  }
  
  public LocalDateTime getStarted() {
    return started;
  }
  
  public void setStarted(LocalDateTime started) {
    this.started = started;
  }
  
  public LocalDateTime getFinished() {
    return finished;
  }
  
  public void setFinished(LocalDateTime finished) {
    this.finished = finished;
  }
  
  public String getError() {
    return error;
  }
  
  public void setError(String error) {
    this.error = error;
  }
  
  public String getTextTree() {
    return textTree;
  }
  
  public void setTextTree(String textTree) {
    this.textTree = textTree;
  }
  
  public Set<String> getNames() {
    return names;
  }
  
  public void setNames(Set<String> names) {
    this.names = names;
  }
  
  public Integer getDescriptionCount() {
    return descriptionCount;
  }
  
  public void setDescriptionCount(Integer descriptionCount) {
    this.descriptionCount = descriptionCount;
  }
  
  public Integer getDistributionCount() {
    return distributionCount;
  }
  
  public void setDistributionCount(Integer distributionCount) {
    this.distributionCount = distributionCount;
  }
  
  public Integer getMediaCount() {
    return mediaCount;
  }
  
  public void setMediaCount(Integer mediaCount) {
    this.mediaCount = mediaCount;
  }
  
  public Integer getNameCount() {
    return nameCount;
  }
  
  public void setNameCount(Integer nameCount) {
    this.nameCount = nameCount;
  }
  
  public Integer getReferenceCount() {
    return referenceCount;
  }
  
  public void setReferenceCount(Integer referenceCount) {
    this.referenceCount = referenceCount;
  }
  
  public Integer getTaxonCount() {
    return taxonCount;
  }
  
  public void setTaxonCount(Integer taxonCount) {
    this.taxonCount = taxonCount;
  }
  
  public Integer getVernacularCount() {
    return vernacularCount;
  }
  
  public void setVernacularCount(Integer vernacularCount) {
    this.vernacularCount = vernacularCount;
  }
  
  public Map<Issue, Integer> getIssueCount() {
    return issueCount;
  }
  
  public void setIssueCount(Map<Issue, Integer> issueCount) {
    this.issueCount = issueCount;
  }
  
  public StatusRankCounts getUsagesByRankCount() {
    return usagesByRankCount;
  }
  
  public void setUsagesByRankCount(StatusRankCounts usagesByRankCount) {
    this.usagesByRankCount = usagesByRankCount;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SectorImport that = (SectorImport) o;
    return sectorKey == that.sectorKey &&
        attempt == that.attempt &&
        state == that.state &&
        Objects.equals(started, that.started) &&
        Objects.equals(finished, that.finished) &&
        Objects.equals(error, that.error) &&
        Objects.equals(textTree, that.textTree) &&
        Objects.equals(names, that.names) &&
        Objects.equals(descriptionCount, that.descriptionCount) &&
        Objects.equals(distributionCount, that.distributionCount) &&
        Objects.equals(mediaCount, that.mediaCount) &&
        Objects.equals(nameCount, that.nameCount) &&
        Objects.equals(referenceCount, that.referenceCount) &&
        Objects.equals(taxonCount, that.taxonCount) &&
        Objects.equals(vernacularCount, that.vernacularCount) &&
        Objects.equals(issueCount, that.issueCount) &&
        Objects.equals(usagesByRankCount, that.usagesByRankCount);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(sectorKey, attempt, state, started, finished, error, textTree, names, descriptionCount, distributionCount, mediaCount, nameCount, referenceCount, taxonCount, vernacularCount, issueCount, usagesByRankCount);
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
  
}
