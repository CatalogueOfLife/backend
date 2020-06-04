package life.catalogue.api.model;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 *
 */
public class ParsedNameUsage {
  private Name name;
  private String taxonomicNote;
  private String publishedIn;

  public ParsedNameUsage() {
  }
  
  public ParsedNameUsage(Name name, String taxonomicNote, String publishedIn) {
    this.name = name;
    this.taxonomicNote = taxonomicNote;
    this.publishedIn = publishedIn;
  }
  
  public Name getName() {
    return name;
  }
  
  public void setName(Name name) {
    this.name = name;
  }
  
  public String getTaxonomicNote() {
    return taxonomicNote;
  }
  
  public void setTaxonomicNote(String taxonomicNote) {
    this.taxonomicNote = taxonomicNote;
  }
  
  public void addAccordingTo(String accordingTo) {
    if (!StringUtils.isBlank(accordingTo)) {
      this.taxonomicNote = this.taxonomicNote == null ? accordingTo.trim() : this.taxonomicNote + " " + accordingTo.trim();
    }
  }

  public String getPublishedIn() {
    return publishedIn;
  }

  public void setPublishedIn(String publishedIn) {
    this.publishedIn = publishedIn;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ParsedNameUsage)) return false;
    ParsedNameUsage that = (ParsedNameUsage) o;
    return Objects.equals(name, that.name) &&
      Objects.equals(taxonomicNote, that.taxonomicNote) &&
      Objects.equals(publishedIn, that.publishedIn);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, taxonomicNote, publishedIn);
  }
}
