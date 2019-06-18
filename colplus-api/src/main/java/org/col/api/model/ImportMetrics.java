package org.col.api.model;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.Maps;
import org.col.api.vocab.*;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

public class ImportMetrics<T extends Enum> implements ImportAttempt {
  
  private Integer datasetKey;

  /**
   * Sequential attempt number starting with 1 for each dataset/sector
   */
  private int attempt;

  /**
   * State of the import, e.g. indicating if still running, success or failure.
   */
  private T state;
  
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
  private Integer nameCount;
  private Integer taxonCount;
  private Integer synonymCount;
  private Integer referenceCount;
  private Integer descriptionCount;
  private Integer distributionCount;
  private Integer mediaCount;
  private Integer vernacularCount;
  private Map<NameType, Integer> namesByTypeCount = Maps.newHashMap();
  private Map<NomStatus, Integer> namesByStatusCount = Maps.newHashMap();
  private Map<Origin, Integer> namesByOriginCount = Maps.newHashMap();
  private Map<Rank, Integer> namesByRankCount = Maps.newHashMap();
  private Map<NomRelType, Integer> nameRelationsByTypeCount = Maps.newHashMap();
  private Map<Gazetteer, Integer> distributionsByGazetteerCount = Maps.newHashMap();
  private Map<String, Integer> vernacularsByLanguageCount = Maps.newHashMap();
  private Map<MediaType, Integer> mediaByTypeCount = Maps.newHashMap();
  private Map<TaxonomicStatus, Integer> usagesByStatusCount = Maps.newHashMap();
  private Map<Rank, Integer> taxaByRankCount = Maps.newHashMap();
  private Map<Issue, Integer> issuesCount = Maps.newHashMap();
  
  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
  }
  
  @Override
  public int getAttempt() {
    return attempt;
  }
  
  @Override
  public void setAttempt(int attempt) {
    this.attempt = attempt;
  }
  
  public T getState() {
    return state;
  }
  
  public void setState(T state) {
    this.state = state;
  }
  
  @Override
  public LocalDateTime getStarted() {
    return started;
  }
  
  @Override
  public void setStarted(LocalDateTime started) {
    this.started = started;
  }
  
  @Override
  public LocalDateTime getFinished() {
    return finished;
  }
  
  @Override
  public void setFinished(LocalDateTime finished) {
    this.finished = finished;
  }
  
  @Override
  public String getError() {
    return error;
  }
  
  @Override
  public void setError(String error) {
    this.error = error;
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
  
  public Integer getSynonymCount() {
    return synonymCount;
  }
  
  public void setSynonymCount(Integer synonymCount) {
    this.synonymCount = synonymCount;
  }
  
  public Integer getReferenceCount() {
    return referenceCount;
  }
  
  public void setReferenceCount(Integer referenceCount) {
    this.referenceCount = referenceCount;
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
  
  public Integer getVernacularCount() {
    return vernacularCount;
  }
  
  public void setVernacularCount(Integer vernacularCount) {
    this.vernacularCount = vernacularCount;
  }
  
  public Map<NameType, Integer> getNamesByTypeCount() {
    return namesByTypeCount;
  }
  
  public void setNamesByTypeCount(Map<NameType, Integer> namesByTypeCount) {
    this.namesByTypeCount = namesByTypeCount;
  }
  
  public Map<NomStatus, Integer> getNamesByStatusCount() {
    return namesByStatusCount;
  }
  
  public void setNamesByStatusCount(Map<NomStatus, Integer> namesByStatusCount) {
    this.namesByStatusCount = namesByStatusCount;
  }
  
  public Map<Origin, Integer> getNamesByOriginCount() {
    return namesByOriginCount;
  }
  
  public void setNamesByOriginCount(Map<Origin, Integer> namesByOriginCount) {
    this.namesByOriginCount = namesByOriginCount;
  }
  
  public Map<Rank, Integer> getNamesByRankCount() {
    return namesByRankCount;
  }
  
  public void setNamesByRankCount(Map<Rank, Integer> namesByRankCount) {
    this.namesByRankCount = namesByRankCount;
  }
  
  public Map<NomRelType, Integer> getNameRelationsByTypeCount() {
    return nameRelationsByTypeCount;
  }
  
  public void setNameRelationsByTypeCount(Map<NomRelType, Integer> nameRelationsByTypeCount) {
    this.nameRelationsByTypeCount = nameRelationsByTypeCount;
  }
  
  public Map<Gazetteer, Integer> getDistributionsByGazetteerCount() {
    return distributionsByGazetteerCount;
  }
  
  public void setDistributionsByGazetteerCount(Map<Gazetteer, Integer> distributionsByGazetteerCount) {
    this.distributionsByGazetteerCount = distributionsByGazetteerCount;
  }
  
  public Map<String, Integer> getVernacularsByLanguageCount() {
    return vernacularsByLanguageCount;
  }
  
  public void setVernacularsByLanguageCount(Map<String, Integer> vernacularsByLanguageCount) {
    this.vernacularsByLanguageCount = vernacularsByLanguageCount;
  }
  
  public Map<MediaType, Integer> getMediaByTypeCount() {
    return mediaByTypeCount;
  }
  
  public void setMediaByTypeCount(Map<MediaType, Integer> mediaByTypeCount) {
    this.mediaByTypeCount = mediaByTypeCount;
  }
  
  public Map<TaxonomicStatus, Integer> getUsagesByStatusCount() {
    return usagesByStatusCount;
  }
  
  public void setUsagesByStatusCount(Map<TaxonomicStatus, Integer> usagesByStatusCount) {
    this.usagesByStatusCount = usagesByStatusCount;
  }
  
  /**
   * @return count of all usages, i.e. all taxa and synonyms, regardless of their status
   */
  public int getUsagesCount() {
    return usagesByStatusCount == null ? 0 : usagesByStatusCount.values().stream().mapToInt(Integer::intValue).sum();
  }
  
  public Map<Rank, Integer> getTaxaByRankCount() {
    return taxaByRankCount;
  }
  
  public void setTaxaByRankCount(Map<Rank, Integer> taxaByRankCount) {
    this.taxaByRankCount = taxaByRankCount;
  }
  
  public Map<Issue, Integer> getIssuesCount() {
    return issuesCount;
  }
  
  public void setIssuesCount(Map<Issue, Integer> issuesCount) {
    this.issuesCount = issuesCount;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ImportMetrics<?> that = (ImportMetrics<?>) o;
    return attempt == that.attempt &&
        Objects.equals(state, that.state) &&
        Objects.equals(started, that.started) &&
        Objects.equals(finished, that.finished) &&
        Objects.equals(error, that.error) &&
        Objects.equals(nameCount, that.nameCount) &&
        Objects.equals(taxonCount, that.taxonCount) &&
        Objects.equals(synonymCount, that.synonymCount) &&
        Objects.equals(referenceCount, that.referenceCount) &&
        Objects.equals(descriptionCount, that.descriptionCount) &&
        Objects.equals(distributionCount, that.distributionCount) &&
        Objects.equals(mediaCount, that.mediaCount) &&
        Objects.equals(vernacularCount, that.vernacularCount) &&
        Objects.equals(namesByTypeCount, that.namesByTypeCount) &&
        Objects.equals(namesByStatusCount, that.namesByStatusCount) &&
        Objects.equals(namesByOriginCount, that.namesByOriginCount) &&
        Objects.equals(namesByRankCount, that.namesByRankCount) &&
        Objects.equals(nameRelationsByTypeCount, that.nameRelationsByTypeCount) &&
        Objects.equals(distributionsByGazetteerCount, that.distributionsByGazetteerCount) &&
        Objects.equals(vernacularsByLanguageCount, that.vernacularsByLanguageCount) &&
        Objects.equals(mediaByTypeCount, that.mediaByTypeCount) &&
        Objects.equals(usagesByStatusCount, that.usagesByStatusCount) &&
        Objects.equals(taxaByRankCount, that.taxaByRankCount) &&
        Objects.equals(issuesCount, that.issuesCount);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, attempt, state, started, finished, error, nameCount, taxonCount, synonymCount, referenceCount, descriptionCount, distributionCount, mediaCount, vernacularCount, namesByTypeCount, namesByStatusCount, namesByOriginCount, namesByRankCount, nameRelationsByTypeCount, distributionsByGazetteerCount, vernacularsByLanguageCount, mediaByTypeCount, usagesByStatusCount, taxaByRankCount, issuesCount);
  }
  
  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" +
        attempt() +
        ": " + state +
        ", started=" + started +
        ", finished=" + finished +
        ", names=" + nameCount +
        ", taxa=" + taxonCount +
        '}';
  }
  
}
