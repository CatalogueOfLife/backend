package life.catalogue.api.model;

import com.google.common.collect.Maps;
import life.catalogue.api.vocab.*;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static life.catalogue.api.util.ObjectUtils.coalesce;

public class ImportMetrics implements ImportAttempt {

  private Integer datasetKey;

  /**
   * Sequential attempt number starting with 1 for each dataset/sector
   */
  private int attempt;

  /**
   * Specific job that created the import, i.e. job class that generated this import
   */
  private String job;

  /**
   * State of the import, e.g. indicating if still running, success or failure.
   */
  private ImportState state;
  
  /**
   * Time the import command started
   */
  private LocalDateTime started;
  
  /**
   * Time the import command finished
   */
  private LocalDateTime finished;
  private Integer createdBy;
  private String error;

  // metrics
  private Integer nameCount;
  private Integer taxonCount;
  private Integer synonymCount;
  private Integer referenceCount;
  private Integer typeMaterialCount;
  private Integer distributionCount;
  private Integer mediaCount;
  private Integer treatmentCount;
  private Integer vernacularCount;
  private Map<NameType, Integer> namesByTypeCount = Maps.newHashMap();
  private Map<NomStatus, Integer> namesByStatusCount = Maps.newHashMap();
  private Map<Origin, Integer> namesByOriginCount = Maps.newHashMap();
  private Map<Rank, Integer> namesByRankCount = Maps.newHashMap();
  private Map<NomRelType, Integer> nameRelationsByTypeCount = Maps.newHashMap();
  private Map<TypeStatus, Integer> typeMaterialByStatusCount = Maps.newHashMap();
  private Map<Gazetteer, Integer> distributionsByGazetteerCount = Maps.newHashMap();
  private Map<String, Integer> vernacularsByLanguageCount = Maps.newHashMap();
  private Map<MediaType, Integer> mediaByTypeCount = Maps.newHashMap();
  private Map<TaxonomicStatus, Integer> usagesByStatusCount = Maps.newHashMap();
  private Map<Rank, Integer> taxaByRankCount = Maps.newHashMap();
  private Map<TaxRelType, Integer> taxonRelationsByTypeCount = Maps.newHashMap();
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
  
  public ImportState getState() {
    return state;
  }
  
  public void setState(ImportState state) {
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

  public Integer getCreatedBy() {
    return createdBy;
  }

  public void setCreatedBy(Integer createdBy) {
    this.createdBy = createdBy;
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

  public Integer getTreatmentCount() {
    return treatmentCount;
  }

  public void setTreatmentCount(Integer treatmentCount) {
    this.treatmentCount = treatmentCount;
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

  public Integer getTypeMaterialCount() {
    return typeMaterialCount;
  }

  public void setTypeMaterialCount(Integer typeMaterialCount) {
    this.typeMaterialCount = typeMaterialCount;
  }

  public Map<TypeStatus, Integer> getTypeMaterialByStatusCount() {
    return typeMaterialByStatusCount;
  }

  public void setTypeMaterialByStatusCount(Map<TypeStatus, Integer> typeMaterialByStatusCount) {
    this.typeMaterialByStatusCount = typeMaterialByStatusCount;
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

  public Map<TaxRelType, Integer> getTaxonRelationsByTypeCount() {
    return taxonRelationsByTypeCount;
  }

  public void setTaxonRelationsByTypeCount(Map<TaxRelType, Integer> taxonRelationsByTypeCount) {
    this.taxonRelationsByTypeCount = taxonRelationsByTypeCount;
  }

  public Map<Issue, Integer> getIssuesCount() {
    return issuesCount;
  }
  
  public void setIssuesCount(Map<Issue, Integer> issuesCount) {
    this.issuesCount = issuesCount;
  }

  public String getJob() {
    return job;
  }

  public void setJob(String job) {
    this.job = job;
  }

  public void add(ImportMetrics m) {
    nameCount = sum(nameCount, m.nameCount);
    taxonCount = sum(taxonCount, m.taxonCount);
    synonymCount = sum(synonymCount, m.synonymCount);
    referenceCount = sum(referenceCount, m.referenceCount);
    typeMaterialCount = sum(typeMaterialCount, m.typeMaterialCount);
    distributionCount = sum(distributionCount, m.distributionCount);
    mediaCount = sum(mediaCount, m.mediaCount);
    treatmentCount = sum(treatmentCount, m.treatmentCount);
    vernacularCount = sum(vernacularCount, m.vernacularCount);

    namesByTypeCount = sum(namesByTypeCount, m.namesByTypeCount);
    namesByStatusCount = sum(namesByStatusCount, m.namesByStatusCount);
    namesByOriginCount = sum(namesByOriginCount, m.namesByOriginCount);
    namesByRankCount = sum(namesByRankCount, m.namesByRankCount);
    nameRelationsByTypeCount = sum(nameRelationsByTypeCount, m.nameRelationsByTypeCount);
    typeMaterialByStatusCount = sum(typeMaterialByStatusCount, m.typeMaterialByStatusCount);
    distributionsByGazetteerCount = sum(distributionsByGazetteerCount, m.distributionsByGazetteerCount);
    vernacularsByLanguageCount = sum(vernacularsByLanguageCount, m.vernacularsByLanguageCount);
    mediaByTypeCount = sum(mediaByTypeCount, m.mediaByTypeCount);
    usagesByStatusCount = sum(usagesByStatusCount, m.usagesByStatusCount);
    taxaByRankCount = sum(taxaByRankCount, m.taxaByRankCount);
    taxonRelationsByTypeCount = sum(taxonRelationsByTypeCount, m.taxonRelationsByTypeCount);
    issuesCount = sum(issuesCount, m.issuesCount);
  }

  private static Integer sum(Integer cnt1, Integer cnt2) {
    if (cnt1 == null && cnt2 == null) return null;
    return coalesce(cnt1,0) + coalesce(cnt2,0);
  }

  private static <T> Map<T, Integer> sum(Map<T, Integer> cnt1, Map<T, Integer> cnt2) {
    Map<T, Integer> sum = cnt1 == null ? new HashMap<>() : new HashMap<>(cnt1);
    if (cnt2 != null) {
      for (Map.Entry<T, Integer> x : cnt2.entrySet()) {
        if (x.getValue() != null) {
          if (sum.containsKey(x.getKey())) {
            Integer cnt = sum.get(x.getKey());
            sum.put(x.getKey(), cnt + x.getValue());
          } else {
            sum.put(x.getKey(), x.getValue());
          }
        }
      }
    }
    return sum;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ImportMetrics)) return false;
    ImportMetrics that = (ImportMetrics) o;
    return attempt == that.attempt &&
      Objects.equals(datasetKey, that.datasetKey) &&
      Objects.equals(job, that.job) &&
      state == that.state &&
      Objects.equals(started, that.started) &&
      Objects.equals(finished, that.finished) &&
      Objects.equals(createdBy, that.createdBy) &&
      Objects.equals(error, that.error) &&
      Objects.equals(nameCount, that.nameCount) &&
      Objects.equals(taxonCount, that.taxonCount) &&
      Objects.equals(synonymCount, that.synonymCount) &&
      Objects.equals(referenceCount, that.referenceCount) &&
      Objects.equals(typeMaterialCount, that.typeMaterialCount) &&
      Objects.equals(distributionCount, that.distributionCount) &&
      Objects.equals(mediaCount, that.mediaCount) &&
      Objects.equals(treatmentCount, that.treatmentCount) &&
      Objects.equals(vernacularCount, that.vernacularCount) &&
      Objects.equals(namesByTypeCount, that.namesByTypeCount) &&
      Objects.equals(namesByStatusCount, that.namesByStatusCount) &&
      Objects.equals(namesByOriginCount, that.namesByOriginCount) &&
      Objects.equals(namesByRankCount, that.namesByRankCount) &&
      Objects.equals(nameRelationsByTypeCount, that.nameRelationsByTypeCount) &&
      Objects.equals(typeMaterialByStatusCount, that.typeMaterialByStatusCount) &&
      Objects.equals(distributionsByGazetteerCount, that.distributionsByGazetteerCount) &&
      Objects.equals(vernacularsByLanguageCount, that.vernacularsByLanguageCount) &&
      Objects.equals(mediaByTypeCount, that.mediaByTypeCount) &&
      Objects.equals(usagesByStatusCount, that.usagesByStatusCount) &&
      Objects.equals(taxaByRankCount, that.taxaByRankCount) &&
      Objects.equals(taxonRelationsByTypeCount, that.taxonRelationsByTypeCount) &&
      Objects.equals(issuesCount, that.issuesCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, attempt, job, state, started, finished, createdBy, error, nameCount, taxonCount, synonymCount, referenceCount, typeMaterialCount, distributionCount, mediaCount, treatmentCount, vernacularCount, namesByTypeCount, namesByStatusCount, namesByOriginCount, namesByRankCount, nameRelationsByTypeCount, typeMaterialByStatusCount, distributionsByGazetteerCount, vernacularsByLanguageCount, mediaByTypeCount, usagesByStatusCount, taxaByRankCount, taxonRelationsByTypeCount, issuesCount);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + "{" + job + " " + attempt() +
      ": " + state +
      ", started=" + started +
      ", finished=" + finished +
      ", names=" + nameCount +
      ", taxa=" + taxonCount +
      '}';
  }
  
}
