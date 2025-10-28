package life.catalogue.api.model;

import life.catalogue.api.vocab.License;
import life.catalogue.api.vocab.MediaType;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;

public class Media extends DatasetScopedEntity<Integer> implements ExtensionEntity {

  private Integer sectorKey;
  private Sector.Mode sectorMode;
  private Integer verbatimKey;
  private Integer verbatimSourceKey;
  private URI url;
  private URI thumbnail;
  private MediaType type;
  private String format;
  private String title;
  private LocalDate captured;
  private String capturedBy;
  private License license;
  private URI link;
  private String referenceId;
  private String remarks;

  @Override
  public Integer getSectorKey() {
    return sectorKey;
  }

  @Override
  public void setSectorKey(Integer sectorKey) {
    this.sectorKey = sectorKey;
  }

  @Override
  public Sector.Mode getSectorMode() {
    return sectorMode;
  }

  @Override
  public void setSectorMode(Sector.Mode sectorMode) {
    this.sectorMode = sectorMode;
  }

  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }

  public Integer getVerbatimSourceKey() {
    return verbatimSourceKey;
  }

  public void setVerbatimSourceKey(Integer verbatimSourceKey) {
    this.verbatimSourceKey = verbatimSourceKey;
  }

  public URI getUrl() {
    return url;
  }
  
  public void setUrl(URI url) {
    this.url = url;
  }

  public URI getThumbnail() {
    return thumbnail;
  }

  public void setThumbnail(URI thumbnail) {
    this.thumbnail = thumbnail;
  }

  public MediaType getType() {
    return type;
  }
  
  public void setType(MediaType type) {
    this.type = type;
  }
  
  public String getFormat() {
    return format;
  }
  
  public void setFormat(String format) {
    this.format = format;
  }
  
  public String getTitle() {
    return title;
  }
  
  public void setTitle(String title) {
    this.title = title;
  }

  public LocalDate getCaptured() {
    return captured;
  }

  public void setCaptured(LocalDate captured) {
    this.captured = captured;
  }

  public String getCapturedBy() {
    return capturedBy;
  }

  public void setCapturedBy(String capturedBy) {
    this.capturedBy = capturedBy;
  }

  public License getLicense() {
    return license;
  }
  
  public void setLicense(License license) {
    this.license = license;
  }
  
  public URI getLink() {
    return link;
  }
  
  public void setLink(URI link) {
    this.link = link;
  }
  
  @Override
  public String getReferenceId() {
    return referenceId;
  }
  
  @Override
  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }

  @Override
  public String getRemarks() {
    return remarks;
  }

  @Override
  public void setRemarks(String remarks) {
    this.remarks = remarks;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof Media)) return false;
    if (!super.equals(o)) return false;

    Media media = (Media) o;
    return Objects.equals(sectorKey, media.sectorKey) &&
      sectorMode == media.sectorMode &&
      Objects.equals(verbatimKey, media.verbatimKey) &&
      Objects.equals(verbatimSourceKey, media.verbatimSourceKey) &&
      Objects.equals(url, media.url) &&
      Objects.equals(thumbnail, media.thumbnail) &&
      type == media.type &&
      Objects.equals(format, media.format) &&
      Objects.equals(title, media.title) &&
      Objects.equals(captured, media.captured) &&
      Objects.equals(capturedBy, media.capturedBy) &&
      license == media.license &&
      Objects.equals(link, media.link) &&
      Objects.equals(referenceId, media.referenceId) &&
      Objects.equals(remarks, media.remarks);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), sectorKey, sectorMode, verbatimKey, verbatimSourceKey, url, thumbnail, type, format, title, captured, capturedBy, license, link, referenceId, remarks);
  }
}
