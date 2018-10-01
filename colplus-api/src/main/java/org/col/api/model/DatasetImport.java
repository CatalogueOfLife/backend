package org.col.api.model;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.Maps;
import org.col.api.vocab.*;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

/**
 * Metrics and import details about a single dataset import event.
 */
public class DatasetImport {
  private Integer datasetKey;
  /**
   * Sequential attempt number starting with 1 for each dataset
   */
  private Integer attempt;
  
  /**
   * State of the import, indicating if still running, success or failure.
   */
  private ImportState state;
  
  private URI downloadUri;
  
  /**
   * Last modification date of the downloaded file
   */
  private LocalDateTime download;
  
  /**
   * Time the import command started
   */
  private LocalDateTime started;
  
  /**
   * Time the import command finished
   */
  private LocalDateTime finished;
  private String error;
  
  // metrics
  private Integer verbatimCount;
  private Integer nameCount;
  private Integer taxonCount;
  private Integer referenceCount;
  private Integer vernacularCount;
  private Integer distributionCount;
  private Map<Issue, Integer> issuesCount = Maps.newHashMap();
  private Map<Rank, Integer> namesByRankCount = Maps.newHashMap();
  private Map<NameType, Integer> namesByTypeCount = Maps.newHashMap();
  private Map<Language, Integer> vernacularsByLanguageCount = Maps.newHashMap();
  private Map<Gazetteer, Integer> distributionsByGazetteerCount = Maps.newHashMap();
  private Map<Origin, Integer> namesByOriginCount = Maps.newHashMap();
  private Map<TaxonomicStatus, Integer> usagesByStatusCount = Maps.newHashMap();
  private Map<NomStatus, Integer> namesByStatusCount = Maps.newHashMap();
  private Map<NomRelType, Integer> nameRelationsByTypeCount = Maps.newHashMap();
  private Map<Term, Integer> verbatimByTypeCount = Maps.newHashMap();
  
  public Integer getAttempt() {
    return attempt;
  }
  
  public void setAttempt(Integer attempt) {
    this.attempt = attempt;
  }
  
  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }
  
  public ImportState getState() {
    return state;
  }
  
  public void setState(ImportState state) {
    this.state = state;
  }
  
  public URI getDownloadUri() {
    return downloadUri;
  }
  
  public void setDownloadUri(URI downloadUri) {
    this.downloadUri = downloadUri;
  }
  
  public LocalDateTime getDownload() {
    return download;
  }
  
  public void setDownload(LocalDateTime download) {
    this.download = download;
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
  
  public Integer getVerbatimCount() {
    return verbatimCount;
  }
  
  public void setVerbatimCount(Integer verbatimCount) {
    this.verbatimCount = verbatimCount;
  }
  
  public Integer getNameCount() {
    return nameCount;
  }
  
  public void setNameCount(Integer nameCount) {
    this.nameCount = nameCount;
  }
  
  public Integer getTaxonCount() {
    return taxonCount;
  }
  
  public void setTaxonCount(Integer taxonCount) {
    this.taxonCount = taxonCount;
  }
  
  public Integer getReferenceCount() {
    return referenceCount;
  }
  
  public void setReferenceCount(Integer referenceCount) {
    this.referenceCount = referenceCount;
  }
  
  public Integer getVernacularCount() {
    return vernacularCount;
  }
  
  public void setVernacularCount(Integer vernacularCount) {
    this.vernacularCount = vernacularCount;
  }
  
  public Integer getDistributionCount() {
    return distributionCount;
  }
  
  public void setDistributionCount(Integer distributionCount) {
    this.distributionCount = distributionCount;
  }
  
  public Map<Issue, Integer> getIssuesCount() {
    return issuesCount;
  }
  
  public void setIssuesCount(Map<Issue, Integer> issuesCount) {
    this.issuesCount = issuesCount;
  }
  
  public Map<Rank, Integer> getNamesByRankCount() {
    return namesByRankCount;
  }
  
  public void setNamesByRankCount(Map<Rank, Integer> namesByRankCount) {
    this.namesByRankCount = namesByRankCount;
  }
  
  public Map<NameType, Integer> getNamesByTypeCount() {
    return namesByTypeCount;
  }
  
  public void setNamesByTypeCount(Map<NameType, Integer> namesByTypeCount) {
    this.namesByTypeCount = namesByTypeCount;
  }
  
  public Map<Language, Integer> getVernacularsByLanguageCount() {
    return vernacularsByLanguageCount;
  }
  
  public void setVernacularsByLanguageCount(Map<Language, Integer> vernacularsByLanguageCount) {
    this.vernacularsByLanguageCount = vernacularsByLanguageCount;
  }
  
  public Map<Gazetteer, Integer> getDistributionsByGazetteerCount() {
    return distributionsByGazetteerCount;
  }
  
  public void setDistributionsByGazetteerCount(Map<Gazetteer, Integer> distributionsByGazetteerCount) {
    this.distributionsByGazetteerCount = distributionsByGazetteerCount;
  }
  
  public Map<Origin, Integer> getNamesByOriginCount() {
    return namesByOriginCount;
  }
  
  public void setNamesByOriginCount(Map<Origin, Integer> namesByOriginCount) {
    this.namesByOriginCount = namesByOriginCount;
  }
  
  public Map<TaxonomicStatus, Integer> getUsagesByStatusCount() {
    return usagesByStatusCount;
  }
  
  public void setUsagesByStatusCount(Map<TaxonomicStatus, Integer> usagesByStatusCount) {
    this.usagesByStatusCount = usagesByStatusCount;
  }
  
  public Map<NomStatus, Integer> getNamesByStatusCount() {
    return namesByStatusCount;
  }
  
  public void setNamesByStatusCount(Map<NomStatus, Integer> namesByStatusCount) {
    this.namesByStatusCount = namesByStatusCount;
  }
  
  public Map<NomRelType, Integer> getNameRelationsByTypeCount() {
    return nameRelationsByTypeCount;
  }
  
  public void setNameRelationsByTypeCount(Map<NomRelType, Integer> nameRelationsByTypeCount) {
    this.nameRelationsByTypeCount = nameRelationsByTypeCount;
  }
  
  public Map<Term, Integer> getVerbatimByTypeCount() {
    return verbatimByTypeCount;
  }
  
  public void setVerbatimByTypeCount(Map<Term, Integer> verbatimByTypeCount) {
    this.verbatimByTypeCount = verbatimByTypeCount;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    DatasetImport that = (DatasetImport) o;
    return Objects.equals(datasetKey, that.datasetKey) &&
        Objects.equals(attempt, that.attempt) &&
        state == that.state &&
        Objects.equals(downloadUri, that.downloadUri) &&
        Objects.equals(download, that.download) &&
        Objects.equals(started, that.started) &&
        Objects.equals(finished, that.finished) &&
        Objects.equals(error, that.error) &&
        Objects.equals(verbatimCount, that.verbatimCount) &&
        Objects.equals(nameCount, that.nameCount) &&
        Objects.equals(taxonCount, that.taxonCount) &&
        Objects.equals(referenceCount, that.referenceCount) &&
        Objects.equals(vernacularCount, that.vernacularCount) &&
        Objects.equals(distributionCount, that.distributionCount) &&
        Objects.equals(issuesCount, that.issuesCount) &&
        Objects.equals(namesByRankCount, that.namesByRankCount) &&
        Objects.equals(namesByTypeCount, that.namesByTypeCount) &&
        Objects.equals(vernacularsByLanguageCount, that.vernacularsByLanguageCount) &&
        Objects.equals(distributionsByGazetteerCount, that.distributionsByGazetteerCount) &&
        Objects.equals(namesByOriginCount, that.namesByOriginCount) &&
        Objects.equals(usagesByStatusCount, that.usagesByStatusCount) &&
        Objects.equals(namesByStatusCount, that.namesByStatusCount) &&
        Objects.equals(nameRelationsByTypeCount, that.nameRelationsByTypeCount) &&
        Objects.equals(verbatimByTypeCount, that.verbatimByTypeCount);
  }
  
  @Override
  public int hashCode() {
    
    return Objects.hash(datasetKey, attempt, state, downloadUri, download, started, finished, error, verbatimCount, nameCount, taxonCount, referenceCount, vernacularCount, distributionCount, issuesCount, namesByRankCount, namesByTypeCount, vernacularsByLanguageCount, distributionsByGazetteerCount, namesByOriginCount, usagesByStatusCount, namesByStatusCount, nameRelationsByTypeCount, verbatimByTypeCount);
  }
  
  public String attempt() {
    return datasetKey + " - " + attempt;
  }
  
  @Override
  public String toString() {
    return "DatasetImport{" +
        attempt() +
        ": state=" + state +
        ", started=" + started +
        ", verbatim=" + verbatimCount +
        ", names=" + nameCount +
        ", taxa=" + taxonCount +
        '}';
  }
}
