package org.col.api.model;

import java.net.URI;
import java.time.LocalDate;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.col.api.vocab.License;
import org.col.api.vocab.MediaType;

public class Media extends DataEntity implements Referenced, VerbatimEntity, IntKey {
  @JsonIgnore
  private Integer key;
  private Integer verbatimKey;
  private URI url;
  private MediaType type;
  private String format;
  private String title;
  private LocalDate captured;
  private String capturedBy;
  private License license;
  private URI link;
  private String referenceId;
  
  @Override
  public Integer getKey() {
    return key;
  }
  
  @Override
  public void setKey(Integer key) {
    this.key = key;
  }
  
  @Override
  public Integer getVerbatimKey() {
    return verbatimKey;
  }
  
  @Override
  public void setVerbatimKey(Integer verbatimKey) {
    this.verbatimKey = verbatimKey;
  }
  
  public URI getUrl() {
    return url;
  }
  
  public void setUrl(URI url) {
    this.url = url;
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
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    Media media = (Media) o;
    return Objects.equals(key, media.key) &&
            Objects.equals(verbatimKey, media.verbatimKey) &&
            Objects.equals(url, media.url) &&
            type == media.type &&
            Objects.equals(format, media.format) &&
            Objects.equals(title, media.title) &&
            Objects.equals(captured, media.captured) &&
            Objects.equals(capturedBy, media.capturedBy) &&
            license == media.license &&
            Objects.equals(link, media.link) &&
            Objects.equals(referenceId, media.referenceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), key, verbatimKey, url, type, format, title, captured, capturedBy, license, link, referenceId);
  }
}
