package org.col.api.model;

import org.col.api.vocab.TaxonomicStatus;

import java.util.Objects;

/**
 *
 */
public class NameAccordingTo implements NameUsage{
  private Name name;
  private String accordingTo;

  @Override
  public Name getName() {
    return name;
  }

  public void setName(Name name) {
    this.name = name;
  }

  @Override
  public String getAccordingTo() {
    return accordingTo;
  }

  public void setAccordingTo(String accordingTo) {
    this.accordingTo = accordingTo;
  }

  @Override
  public TaxonomicStatus getStatus() {
    return null;
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
