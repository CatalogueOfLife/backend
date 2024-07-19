package life.catalogue.matching;

import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.vocab.TabularFormat;

import java.io.File;
import java.util.Objects;

import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.QueryParam;

import org.apache.commons.io.FilenameUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class MatchingRequest extends TreeTraversalParameter {

  @NotNull
  @DefaultValue("TSV")
  @QueryParam("format")
  private TabularFormat format;
  @QueryParam("sourceDatasetKey")
  private Integer sourceDatasetKey;
  private File upload;

  public Integer getSourceDatasetKey() {
    return sourceDatasetKey;
  }

  public void setSourceDatasetKey(Integer sourceDatasetKey) {
    this.sourceDatasetKey = sourceDatasetKey;
  }

  public File getUpload() {
    return upload;
  }

  public void setUpload(File upload) {
    this.upload = upload;
  }

  public TabularFormat getFormat() {
    return format;
  }

  public void setFormat(TabularFormat format) {
    this.format = format;
  }

  @JsonIgnore
  public String resultFileName() {
    StringBuilder sb = new StringBuilder();
    sb.append("match-");
    if (upload != null) {
      sb.append(FilenameUtils.removeExtension(upload.getName()));

    } else {
      sb.append("dataset-");
      sb.append(sourceDatasetKey);
    }
    if (format == TabularFormat.TSV) {
      sb.append(".tsv");
    } else {
      sb.append(".csv");
    }
    return sb.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof MatchingRequest)) return false;
    if (!super.equals(o)) return false;
    MatchingRequest that = (MatchingRequest) o;
    return format == that.format && Objects.equals(sourceDatasetKey, that.sourceDatasetKey) && Objects.equals(upload, that.upload);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), format, sourceDatasetKey, upload);
  }
}
