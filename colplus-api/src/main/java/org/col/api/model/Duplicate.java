package org.col.api.model;

import java.util.Objects;

public class Duplicate {
  
  private NameUsage usage1;
  private NameUsage usage2;
  
  public NameUsage getUsage1() {
    return usage1;
  }
  
  public void setUsage1(NameUsage usage1) {
    this.usage1 = usage1;
  }
  
  public NameUsage getUsage2() {
    return usage2;
  }
  
  public void setUsage2(NameUsage usage2) {
    this.usage2 = usage2;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Duplicate duplicate = (Duplicate) o;
    return Objects.equals(usage1, duplicate.usage1) &&
        Objects.equals(usage2, duplicate.usage2);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(usage1, usage2);
  }
}
