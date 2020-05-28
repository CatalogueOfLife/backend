package life.catalogue.api.model;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 *
 */
public class NameAccordingTo {
  private Name name;
  private String accordingTo;
  private String publishedIn;

  public NameAccordingTo() {
  }
  
  public NameAccordingTo(Name name, String accordingTo, String publishedIn) {
    this.name = name;
    this.accordingTo = accordingTo;
    this.publishedIn = publishedIn;
  }
  
  public Name getName() {
    return name;
  }
  
  public void setName(Name name) {
    this.name = name;
  }
  
  public String getAccordingTo() {
    return accordingTo;
  }
  
  public void setAccordingTo(String accordingTo) {
    this.accordingTo = accordingTo;
  }
  
  public void addAccordingTo(String accordingTo) {
    if (!StringUtils.isBlank(accordingTo)) {
      this.accordingTo = this.accordingTo == null ? accordingTo.trim() : this.accordingTo + " " + accordingTo.trim();
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
    if (!(o instanceof NameAccordingTo)) return false;
    NameAccordingTo that = (NameAccordingTo) o;
    return Objects.equals(name, that.name) &&
      Objects.equals(accordingTo, that.accordingTo) &&
      Objects.equals(publishedIn, that.publishedIn);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, accordingTo, publishedIn);
  }
}
