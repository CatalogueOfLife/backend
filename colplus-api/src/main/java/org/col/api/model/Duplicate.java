package org.col.api.model;

import java.util.Objects;

public class Duplicate {
  
  private NameUsage usage1;
  private EditorialDecision decision1;
  private NameUsage usage2;
  private EditorialDecision decision2;
  
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
  
  public EditorialDecision getDecision1() {
    return decision1;
  }
  
  public void setDecision1(EditorialDecision decision1) {
    this.decision1 = decision1;
  }
  
  public EditorialDecision getDecision2() {
    return decision2;
  }
  
  public void setDecision2(EditorialDecision decision2) {
    this.decision2 = decision2;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Duplicate duplicate = (Duplicate) o;
    return Objects.equals(usage1, duplicate.usage1) &&
        Objects.equals(decision1, duplicate.decision1) &&
        Objects.equals(usage2, duplicate.usage2) &&
        Objects.equals(decision2, duplicate.decision2);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(usage1, decision1, usage2, decision2);
  }
}
