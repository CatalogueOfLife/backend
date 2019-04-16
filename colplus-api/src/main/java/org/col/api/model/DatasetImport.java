package org.col.api.model;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.Maps;
import org.col.api.vocab.ImportState;
import org.gbif.dwc.terms.Term;

/**
 * Metrics and import details about a single dataset import event.
 */
public class DatasetImport extends ImportMetrics<ImportState> {
  
  private Integer datasetKey;
  
  private URI downloadUri;
  
  /**
   * Last modification date of the downloaded file
   */
  private LocalDateTime download;
  
  /**
   * MD5 Hash of raw archive file.
   * Present only if downloaded.
   */
  private String md5;
  
  private Integer verbatimCount;
  
  private Map<Term, Integer> verbatimByTypeCount = Maps.newHashMap();
  
  public Integer getDatasetKey() {
    return datasetKey;
  }
  
  public void setDatasetKey(Integer datasetKey) {
    this.datasetKey = datasetKey;
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
  
  public String getMd5() {
    return md5;
  }
  
  public void setMd5(String md5) {
    this.md5 = md5;
  }
  
  public Integer getVerbatimCount() {
    return verbatimCount;
  }
  
  public void setVerbatimCount(Integer verbatimCount) {
    this.verbatimCount = verbatimCount;
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
    if (!super.equals(o)) return false;
    DatasetImport that = (DatasetImport) o;
    return Objects.equals(datasetKey, that.datasetKey) &&
        Objects.equals(downloadUri, that.downloadUri) &&
        Objects.equals(download, that.download) &&
        Objects.equals(md5, that.md5) &&
        Objects.equals(verbatimCount, that.verbatimCount) &&
        Objects.equals(verbatimByTypeCount, that.verbatimByTypeCount);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), datasetKey, downloadUri, download, md5, verbatimCount, verbatimByTypeCount);
  }
  
  @Override
  public String attempt() {
    return datasetKey + " - " + getAttempt();
  }
  
}
