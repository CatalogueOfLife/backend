package org.col.api;

import java.util.Objects;

/**
 * Simplified literature reference class for proof of concept only.
 */
public class Reference {
  private Integer key;
  private Object csl;
  private Serial serial;
  private Integer year;
  private String doi;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Reference reference = (Reference) o;
    return Objects.equals(key, reference.key) &&
        Objects.equals(csl, reference.csl) &&
        Objects.equals(serial, reference.serial) &&
        Objects.equals(year, reference.year) &&
        Objects.equals(doi, reference.doi);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, csl, serial, year, doi);
  }
}

