package life.catalogue.api.model;

import com.google.common.collect.Maps;
import life.catalogue.api.vocab.*;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
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
  private Integer appliedDecisionCount;
  private Integer bareNameCount;
  private Integer distributionCount;
  private Integer mediaCount;
  private Integer nameCount;
  private Integer referenceCount;
  private Integer sectorCount;
  private Integer synonymCount;
  private Integer taxonCount;
  private Integer treatmentCount;
  private Integer typeMaterialCount;
  private Integer vernacularCount;

  private Map<Gazetteer, Integer> distributionsByGazetteerCount = Maps.newHashMap();
  private Map<Rank, Integer> extinctTaxaByRankCount = Maps.newHashMap();
  private Map<IgnoreReason, Integer> ignoredByReasonCount = Maps.newHashMap();
  private Map<Issue, Integer> issuesCount = Maps.newHashMap();
  private Map<MediaType, Integer> mediaByTypeCount = Maps.newHashMap();
  private Map<NomCode, Integer> namesByCodeCount = Maps.newHashMap();
  private Map<Rank, Integer> namesByRankCount = Maps.newHashMap();
  private Map<NomStatus, Integer> namesByStatusCount = Maps.newHashMap();
  private Map<NameType, Integer> namesByTypeCount = Maps.newHashMap();
  private Map<NomRelType, Integer> nameRelationsByTypeCount = Maps.newHashMap();
  private Map<Rank, Integer> synonymsByRankCount = Maps.newHashMap();
  private Map<Rank, Integer> taxaByRankCount = Maps.newHashMap();
  private Map<TaxRelType, Integer> taxonRelationsByTypeCount = Maps.newHashMap();
  private Map<TypeStatus, Integer> typeMaterialByStatusCount = Maps.newHashMap();
  private Map<TaxonomicStatus, Integer> usagesByStatusCount = Maps.newHashMap();
  private Map<Origin, Integer> usagesByOriginCount = Maps.newHashMap();
  private Map<String, Integer> vernacularsByLanguageCount = Maps.newHashMap();

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

  public Integer getBareNameCount() {
    return bareNameCount;
  }

  public void setBareNameCount(Integer bareNameCount) {
    this.bareNameCount = bareNameCount;
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

  public Map<IgnoreReason, Integer> getIgnoredByReasonCount() {
    return ignoredByReasonCount;
  }

  public void setIgnoredByReasonCount(Map<IgnoreReason, Integer> ignoredByReasonCount) {
    this.ignoredByReasonCount = ignoredByReasonCount;
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

  public Integer getSectorCount() {
    return sectorCount;
  }

  public void setSectorCount(Integer sectorCount) {
    this.sectorCount = sectorCount;
  }

  public Integer getAppliedDecisionCount() {
    return appliedDecisionCount;
  }

  public void setAppliedDecisionCount(Integer appliedDecisionCount) {
    this.appliedDecisionCount = appliedDecisionCount;
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
  
  public Map<NomCode, Integer> getNamesByCodeCount() {
    return namesByCodeCount;
  }

  public void setNamesByCodeCount(Map<NomCode, Integer> namesByCodeCount) {
    this.namesByCodeCount = namesByCodeCount;
  }

  public Map<Origin, Integer> getUsagesByOriginCount() {
    return usagesByOriginCount;
  }

  public void setUsagesByOriginCount(Map<Origin, Integer> usagesByOriginCount) {
    this.usagesByOriginCount = usagesByOriginCount;
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

  public Map<Rank, Integer> getExtinctTaxaByRankCount() {
    return extinctTaxaByRankCount;
  }

  public void setExtinctTaxaByRankCount(Map<Rank, Integer> extinctTaxaByRankCount) {
    this.extinctTaxaByRankCount = extinctTaxaByRankCount;
  }

  public Map<Rank, Integer> getSynonymsByRankCount() {
    return synonymsByRankCount;
  }

  public void setSynonymsByRankCount(Map<Rank, Integer> synonymsByRankCount) {
    this.synonymsByRankCount = synonymsByRankCount;
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
    if (m != null) {
      appliedDecisionCount = sum(appliedDecisionCount, m.appliedDecisionCount);
      bareNameCount = sum(bareNameCount, m.bareNameCount);
      distributionCount = sum(distributionCount, m.distributionCount);
      mediaCount = sum(mediaCount, m.mediaCount);
      nameCount = sum(nameCount, m.nameCount);
      referenceCount = sum(referenceCount, m.referenceCount);
      sectorCount = sum(sectorCount, m.sectorCount);
      synonymCount = sum(synonymCount, m.synonymCount);
      taxonCount = sum(taxonCount, m.taxonCount);
      treatmentCount = sum(treatmentCount, m.treatmentCount);
      typeMaterialCount = sum(typeMaterialCount, m.typeMaterialCount);
      vernacularCount = sum(vernacularCount, m.vernacularCount);

      distributionsByGazetteerCount = sum(distributionsByGazetteerCount, m.distributionsByGazetteerCount);
      extinctTaxaByRankCount = sum(extinctTaxaByRankCount, m.extinctTaxaByRankCount);
      issuesCount = sum(issuesCount, m.issuesCount);
      ignoredByReasonCount = sum(ignoredByReasonCount, m.ignoredByReasonCount);
      mediaByTypeCount = sum(mediaByTypeCount, m.mediaByTypeCount);
      nameRelationsByTypeCount = sum(nameRelationsByTypeCount, m.nameRelationsByTypeCount);
      namesByCodeCount = sum(namesByCodeCount, m.namesByCodeCount);
      namesByRankCount = sum(namesByRankCount, m.namesByRankCount);
      namesByStatusCount = sum(namesByStatusCount, m.namesByStatusCount);
      namesByTypeCount = sum(namesByTypeCount, m.namesByTypeCount);
      synonymsByRankCount = sum(synonymsByRankCount, m.synonymsByRankCount);
      taxaByRankCount = sum(taxaByRankCount, m.taxaByRankCount);
      taxonRelationsByTypeCount = sum(taxonRelationsByTypeCount, m.taxonRelationsByTypeCount);
      typeMaterialByStatusCount = sum(typeMaterialByStatusCount, m.typeMaterialByStatusCount);
      usagesByOriginCount = sum(usagesByOriginCount, m.usagesByOriginCount);
      usagesByStatusCount = sum(usagesByStatusCount, m.usagesByStatusCount);
      vernacularsByLanguageCount = sum(vernacularsByLanguageCount, m.vernacularsByLanguageCount);
    }
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

      Objects.equals(appliedDecisionCount, that.appliedDecisionCount) &&
      Objects.equals(bareNameCount, that.bareNameCount) &&
      Objects.equals(distributionCount, that.distributionCount) &&
      Objects.equals(mediaCount, that.mediaCount) &&
      Objects.equals(nameCount, that.nameCount) &&
      Objects.equals(referenceCount, that.referenceCount) &&
      Objects.equals(sectorCount, that.sectorCount) &&
      Objects.equals(synonymCount, that.synonymCount) &&
      Objects.equals(taxonCount, that.taxonCount) &&
      Objects.equals(treatmentCount, that.treatmentCount) &&
      Objects.equals(typeMaterialCount, that.typeMaterialCount) &&

      Objects.equals(distributionsByGazetteerCount, that.distributionsByGazetteerCount) &&
      Objects.equals(extinctTaxaByRankCount, that.extinctTaxaByRankCount) &&
      Objects.equals(ignoredByReasonCount, that.ignoredByReasonCount) &&
      Objects.equals(issuesCount, that.issuesCount) &&
      Objects.equals(mediaByTypeCount, that.mediaByTypeCount) &&
      Objects.equals(nameRelationsByTypeCount, that.nameRelationsByTypeCount) &&
      Objects.equals(namesByCodeCount, that.namesByCodeCount) &&
      Objects.equals(namesByRankCount, that.namesByRankCount) &&
      Objects.equals(namesByStatusCount, that.namesByStatusCount) &&
      Objects.equals(namesByTypeCount, that.namesByTypeCount) &&
      Objects.equals(synonymsByRankCount, that.synonymsByRankCount) &&
      Objects.equals(taxaByRankCount, that.taxaByRankCount) &&
      Objects.equals(taxonRelationsByTypeCount, that.taxonRelationsByTypeCount) &&
      Objects.equals(typeMaterialByStatusCount, that.typeMaterialByStatusCount) &&
      Objects.equals(usagesByOriginCount, that.usagesByOriginCount) &&
      Objects.equals(usagesByStatusCount, that.usagesByStatusCount) &&
      Objects.equals(vernacularCount, that.vernacularCount) &&
      Objects.equals(vernacularsByLanguageCount, that.vernacularsByLanguageCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(datasetKey, attempt, job, state, started, finished, createdBy, error,
      nameCount, taxonCount, synonymCount, bareNameCount, referenceCount, typeMaterialCount, distributionCount, mediaCount, treatmentCount, vernacularCount,
      sectorCount, ignoredByReasonCount, appliedDecisionCount,
      namesByTypeCount, namesByStatusCount, namesByCodeCount, namesByRankCount, nameRelationsByTypeCount, typeMaterialByStatusCount, distributionsByGazetteerCount,
      vernacularsByLanguageCount, mediaByTypeCount, usagesByOriginCount, usagesByStatusCount,
      taxaByRankCount, extinctTaxaByRankCount, synonymsByRankCount, taxonRelationsByTypeCount, issuesCount);
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
