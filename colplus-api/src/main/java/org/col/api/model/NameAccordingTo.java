package org.col.api.model;

import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

/**
 *
 */
public class NameAccordingTo {
  private Name name;
  private String accordingTo;
  
  public NameAccordingTo() {
  }
  
  public NameAccordingTo(Name name, String accordingTo) {
    this.name = name;
    this.accordingTo = accordingTo;
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
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NameAccordingTo that = (NameAccordingTo) o;
    return Objects.equals(name, that.name) &&
        Objects.equals(accordingTo, that.accordingTo);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(name, accordingTo);
  }
  
}
