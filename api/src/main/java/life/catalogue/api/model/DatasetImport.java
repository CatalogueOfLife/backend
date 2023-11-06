package life.catalogue.api.model;

import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.DatasetOrigin;

import org.gbif.dwc.terms.Term;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import com.google.common.collect.Maps;

/**
 * Metrics and import details about a single dataset import event.
 */
public class DatasetImport extends ImportMetrics {


  /**
   * Maximum number of taxa nested in the classification tree, excluding synonyms.
   */
  private int maxClassificationDepth;

  /**
   * The URI the data was downloaded from before its been imported.
   * In case of uploads this is NULL!
   */
  private URI downloadUri;
  private DatasetOrigin origin;
  private DataFormat format;

  /**
   * Last modification date of the downloaded file
   */
  private LocalDateTime download;
  
  /**
   * MD5 Hash of raw archive file.
   * Present only if downloaded or uploaded.
   */
  private String md5;
  
  private Integer verbatimCount;
  
  private Map<Term, Integer> verbatimByTermCount = Maps.newHashMap();
  
  /**
   * Map of row types that return a map of terms and their counts of verbatim records with that type
   */
  private Map<Term, Map<Term, Integer>> verbatimByRowTypeCount = Maps.newHashMap();

  public URI getDownloadUri() {
    return downloadUri;
  }
  
  public void setDownloadUri(URI downloadUri) {
    this.downloadUri = downloadUri;
  }

  public DatasetOrigin getOrigin() {
    return origin;
  }

  public void setOrigin(DatasetOrigin origin) {
    this.origin = origin;
  }

  public Boolean isUpload() {
    if (origin == DatasetOrigin.EXTERNAL) {
      return downloadUri == null;
    }
    return null;
  }

  public DataFormat getFormat() {
    return format;
  }

  public void setFormat(DataFormat format) {
    this.format = format;
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
  
  public Map<Term, Integer> getVerbatimByTermCount() {
    return verbatimByTermCount;
  }
  
  public void setVerbatimByTermCount(Map<Term, Integer> verbatimByTermCount) {
    this.verbatimByTermCount = verbatimByTermCount;
  }
  
  public Map<Term, Map<Term, Integer>> getVerbatimByRowTypeCount() {
    return verbatimByRowTypeCount;
  }
  
  public void setVerbatimByRowTypeCount(Map<Term, Map<Term, Integer>> verbatimByRowTypeCount) {
    this.verbatimByRowTypeCount = verbatimByRowTypeCount;
  }

  public int getMaxClassificationDepth() {
    return maxClassificationDepth;
  }

  public void setMaxClassificationDepth(int maxClassificationDepth) {
    this.maxClassificationDepth = maxClassificationDepth;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    DatasetImport that = (DatasetImport) o;
    return Objects.equals(downloadUri, that.downloadUri) &&
           maxClassificationDepth == that.maxClassificationDepth &&
        origin == that.origin &&
        format == that.format &&
           Objects.equals(download, that.download) &&
           Objects.equals(md5, that.md5) &&
           Objects.equals(verbatimCount, that.verbatimCount) &&
           Objects.equals(verbatimByTermCount, that.verbatimByTermCount) &&
           Objects.equals(verbatimByRowTypeCount, that.verbatimByRowTypeCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), maxClassificationDepth, downloadUri, origin, format, download, md5, verbatimCount, verbatimByTermCount, verbatimByRowTypeCount);
  }

  @Override
  public String attempt() {
    return getDatasetKey() + " - " + getAttempt();
  }
  
}
