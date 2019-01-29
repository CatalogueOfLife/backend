package org.col.api.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class SectorImport {
  public enum Status {
    WAITING, PREPARING, COPYING, DELETING, INDEXING, FINISHED
  }

  private int sectorKey;
  public Status status;
  
  /**
   * Time the import started
   */
  private LocalDateTime started;
  
  /**
   * Time the import finished
   */
  private LocalDateTime finished;
  private String error;
  
  // change
  public Integer taxaCreated;
  public Integer taxaUpdated;
  public Integer taxaDeleted;
  
  // metrics
  private Integer descriptionCount;
  private Integer distributionCount;
  private Integer mediaCount;
  private Integer nameCount;
  private Integer referenceCount;
  private Integer taxonCount;
  private Integer vernacularCount;
  private StatusRankCounts usagesByRankCount;
  
  public int getSectorKey() {
    return sectorKey;
  }
  
  public void setSectorKey(int sectorKey) {
    this.sectorKey = sectorKey;
  }
  
  public Status getStatus() {
    return status;
  }
  
  public void setStatus(Status status) {
    this.status = status;
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
  
  public Integer getTaxaCreated() {
    return taxaCreated;
  }
  
  public void setTaxaCreated(Integer taxaCreated) {
    this.taxaCreated = taxaCreated;
  }
  
  public Integer getTaxaUpdated() {
    return taxaUpdated;
  }
  
  public void setTaxaUpdated(Integer taxaUpdated) {
    this.taxaUpdated = taxaUpdated;
  }
  
  public Integer getTaxaDeleted() {
    return taxaDeleted;
  }
  
  public void setTaxaDeleted(Integer taxaDeleted) {
    this.taxaDeleted = taxaDeleted;
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
        status == that.status &&
        Objects.equals(started, that.started) &&
        Objects.equals(finished, that.finished) &&
        Objects.equals(error, that.error) &&
        Objects.equals(taxaCreated, that.taxaCreated) &&
        Objects.equals(taxaUpdated, that.taxaUpdated) &&
        Objects.equals(taxaDeleted, that.taxaDeleted) &&
        Objects.equals(descriptionCount, that.descriptionCount) &&
        Objects.equals(distributionCount, that.distributionCount) &&
        Objects.equals(mediaCount, that.mediaCount) &&
        Objects.equals(nameCount, that.nameCount) &&
        Objects.equals(referenceCount, that.referenceCount) &&
        Objects.equals(taxonCount, that.taxonCount) &&
        Objects.equals(vernacularCount, that.vernacularCount) &&
        Objects.equals(usagesByRankCount, that.usagesByRankCount);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(sectorKey, status, started, finished, error, taxaCreated, taxaUpdated, taxaDeleted, descriptionCount, distributionCount, mediaCount, nameCount, referenceCount, taxonCount, vernacularCount, usagesByRankCount);
  }
}
