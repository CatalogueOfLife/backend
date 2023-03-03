package life.catalogue.api.model;

import life.catalogue.api.vocab.JobStatus;

import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.Rank;

import java.time.LocalDateTime;
import java.util.*;

import com.google.common.collect.Maps;

public class DatasetExport extends JobResult {
  private ExportRequest request;
  private List<SimpleName> classification;
  private Integer attempt; // datasets importAttempt when export was generated
  private LocalDateTime started; // export started
  private LocalDateTime finished; // export finished/failed
  private LocalDateTime deleted;  // export file was deleted
  private JobStatus status;
  private String error;
  private Set<Term> truncated;
  // result metrics
  private Integer synonymCount;
  private Integer taxonCount;
  private Map<Rank, Integer> taxaByRankCount = Maps.newHashMap();

  public static DatasetExport createWaiting(UUID key, int userKey, ExportRequest req, Dataset dataset) {
    DatasetExport exp = new DatasetExport();
    exp.setKey(key);
    exp.request = req;
    exp.status = JobStatus.WAITING;
    exp.setCreatedBy(userKey);
    exp.setCreated(LocalDateTime.now());
    exp.attempt = dataset.getAttempt();
    return exp;
  }

  public Integer getAttempt() {
    return attempt;
  }

  public void setAttempt(Integer attempt) {
    this.attempt = attempt;
  }

  public List<SimpleName> getClassification() {
    return classification;
  }

  public void setClassification(List<SimpleName> classification) {
    this.classification = classification;
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

  public LocalDateTime getDeleted() {
    return deleted;
  }

  public void setDeleted(LocalDateTime deleted) {
    this.deleted = deleted;
  }

  public JobStatus getStatus() {
    return status;
  }

  public void setStatus(JobStatus status) {
    this.status = status;
  }

  public Set<Term> getTruncated() {
    return truncated;
  }

  public void setTruncated(Set<Term> truncated) {
    this.truncated = truncated;
  }

  public void addTruncated(Term rowType) {
    if (truncated == null) {
      truncated = new HashSet<>();
    }
    truncated.add(rowType);
  }

  public Integer getSynonymCount() {
    return synonymCount;
  }

  public void setSynonymCount(Integer synonymCount) {
    this.synonymCount = synonymCount;
  }

  public Integer getTaxonCount() {
    return taxonCount;
  }

  public void setTaxonCount(Integer taxonCount) {
    this.taxonCount = taxonCount;
  }

  public Map<Rank, Integer> getTaxaByRankCount() {
    return taxaByRankCount;
  }

  public void setTaxaByRankCount(Map<Rank, Integer> taxaByRankCount) {
    this.taxaByRankCount = taxaByRankCount;
  }

  public String getError() {
    return error;
  }

  public void setError(String error) {
    this.error = error;
  }

  public ExportRequest getRequest() {
    return request;
  }

  public void setRequest(ExportRequest request) {
    this.request = request;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DatasetExport)) return false;
    if (!super.equals(o)) return false;
    DatasetExport that = (DatasetExport) o;
    return Objects.equals(request, that.request)
           && Objects.equals(classification, that.classification)
           && Objects.equals(attempt, that.attempt)
           && Objects.equals(started, that.started)
           && Objects.equals(finished, that.finished)
           && Objects.equals(deleted, that.deleted)
           && status == that.status
           && Objects.equals(error, that.error)
           && Objects.equals(truncated, that.truncated)
           && Objects.equals(synonymCount, that.synonymCount)
           && Objects.equals(taxonCount, that.taxonCount)
           && Objects.equals(taxaByRankCount, that.taxaByRankCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), request, classification, attempt, started, finished, deleted, status, error, truncated, synonymCount, taxonCount, taxaByRankCount);
  }
}
