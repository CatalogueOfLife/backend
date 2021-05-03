package life.catalogue.api.model;

import com.google.common.collect.Maps;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.concurrent.JobStatus;
import org.checkerframework.checker.units.qual.K;
import org.gbif.nameparser.api.Rank;

import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class DatasetExport extends DataEntity<UUID> {
  private UUID key;
  private ExportRequest request;
  private Integer importAttempt; // datasets importAttempt when export was generated
  private LocalDateTime started; // export started
  private LocalDateTime finished; // export finished/failed
  private LocalDateTime deleted;  // export file was deleted
  private JobStatus status;
  private String error;
  // result metrics
  private String md5; // md5 for file
  private long size; // filesize in bytes
  private Integer synonymCount;
  private Integer taxonCount;
  private Map<Rank, Integer> taxaByRankCount = Maps.newHashMap();

  @Override
  public UUID getKey() {
    return key;
  }

  @Override
  public void setKey(UUID key) {
    this.key = key;
  }

  public Integer getImportAttempt() {
    return importAttempt;
  }

  public void setImportAttempt(Integer importAttempt) {
    this.importAttempt = importAttempt;
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

  public String getMd5() {
    return md5;
  }

  public void setMd5(String md5) {
    this.md5 = md5;
  }

  public long getSize() {
    return size;
  }

  public void setSize(long size) {
    this.size = size;
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
    DatasetExport that = (DatasetExport) o;
    return size == that.size && Objects.equals(key, that.key) && Objects.equals(request, that.request) && Objects.equals(importAttempt, that.importAttempt) && Objects.equals(started, that.started) && Objects.equals(finished, that.finished) && Objects.equals(deleted, that.deleted) && status == that.status && Objects.equals(md5, that.md5) && Objects.equals(synonymCount, that.synonymCount) && Objects.equals(taxonCount, that.taxonCount) && Objects.equals(taxaByRankCount, that.taxaByRankCount) && Objects.equals(error, that.error);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, request, importAttempt, started, finished, deleted, status, md5, size, synonymCount, taxonCount, taxaByRankCount, error);
  }
}
