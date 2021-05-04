package life.catalogue.api.model;

import com.google.common.collect.Maps;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.concurrent.JobStatus;
import life.catalogue.common.text.StringUtils;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class DatasetExport extends DataEntity<UUID> {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetExport.class);
  private static URI DOWNLOAD_BASE_URI = URI.create("https://download.catalogueoflife.org/exports");

  private UUID key;
  private ExportRequest request;
  private List<SimpleName> classification;
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

  public static DatasetExport createWaiting(UUID key, int userKey, ExportRequest req, Dataset dataset) {
    DatasetExport exp = new DatasetExport();
    exp.key = key;
    exp.request = req;
    exp.status = JobStatus.WAITING;
    exp.setCreatedBy(userKey);
    exp.setCreated(LocalDateTime.now());
    exp.importAttempt = dataset.getImportAttempt();
    return exp;
  }

  /**
   * @return the relative path to the download base URI that holds the download archive file.
   */
  public static String downloadFilePath(UUID key) {
    return key.toString().substring(0,2) + "/" + key.toString() + ".zip";
  }

  /**
   * @return the final URI that holds the download archive file.
   */
  public static URI downloadURI(UUID key) {
    return DOWNLOAD_BASE_URI.resolve(downloadFilePath(key));
  }

  /**
   * WARNING: Only set this when you know what you're doing!
   */
  public static void setDownloadBaseURI(URI downloadBaseURI) {
    LOG.warn("DownloadBaseURI changed from {} to {}", DatasetExport.DOWNLOAD_BASE_URI, downloadBaseURI);
    DatasetExport.DOWNLOAD_BASE_URI = downloadBaseURI;
  }

  public static class Search {
    private Integer datasetKey;
    private Integer createdBy;
    private JobStatus status;
    private DataFormat format;
    private Integer importAttempt;
    private String taxonID;
    private Rank minRank;
    private Boolean excel;
    private Boolean synonyms;

    public Integer getDatasetKey() {
      return datasetKey;
    }

    public void setDatasetKey(Integer datasetKey) {
      this.datasetKey = datasetKey;
    }

    public Integer getCreatedBy() {
      return createdBy;
    }

    public void setCreatedBy(Integer createdBy) {
      this.createdBy = createdBy;
    }

    public JobStatus getStatus() {
      return status;
    }

    public void setStatus(JobStatus status) {
      this.status = status;
    }

    public DataFormat getFormat() {
      return format;
    }

    public void setFormat(DataFormat format) {
      this.format = format;
    }

    public Integer getImportAttempt() {
      return importAttempt;
    }

    public void setImportAttempt(Integer importAttempt) {
      this.importAttempt = importAttempt;
    }

    public String getTaxonID() {
      return taxonID;
    }

    public void setTaxonID(String taxonID) {
      this.taxonID = taxonID;
    }

    public Rank getMinRank() {
      return minRank;
    }

    public void setMinRank(Rank minRank) {
      this.minRank = minRank;
    }

    public Boolean getExcel() {
      return excel;
    }

    public void setExcel(Boolean excel) {
      this.excel = excel;
    }

    public Boolean getSynonyms() {
      return synonyms;
    }

    public void setSynonyms(Boolean synonyms) {
      this.synonyms = synonyms;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Search)) return false;
      Search search = (Search) o;
      return Objects.equals(datasetKey, search.datasetKey) && Objects.equals(createdBy, search.createdBy) && status == search.status && format == search.format && Objects.equals(importAttempt, search.importAttempt) && Objects.equals(taxonID, search.taxonID) && minRank == search.minRank && Objects.equals(excel, search.excel) && Objects.equals(synonyms, search.synonyms);
    }

    @Override
    public int hashCode() {
      return Objects.hash(datasetKey, createdBy, status, format, importAttempt, taxonID, minRank, excel, synonyms);
    }
  }

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

  public URI getDownload() {
    return downloadURI(key);
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

  public String getSizeWithUnit() {
    return StringUtils.humanReadableByteSize(size);
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
    if (!super.equals(o)) return false;
    DatasetExport that = (DatasetExport) o;
    return size == that.size && Objects.equals(key, that.key) && Objects.equals(request, that.request) && Objects.equals(classification, that.classification) && Objects.equals(importAttempt, that.importAttempt) && Objects.equals(started, that.started) && Objects.equals(finished, that.finished) && Objects.equals(deleted, that.deleted) && status == that.status && Objects.equals(error, that.error) && Objects.equals(md5, that.md5) && Objects.equals(synonymCount, that.synonymCount) && Objects.equals(taxonCount, that.taxonCount) && Objects.equals(taxaByRankCount, that.taxaByRankCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, request, classification, importAttempt, started, finished, deleted, status, error, md5, size, synonymCount, taxonCount, taxaByRankCount);
  }
}
