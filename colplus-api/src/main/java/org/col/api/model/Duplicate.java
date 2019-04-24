package org.col.api.model;

import java.util.List;
import java.util.Objects;

public class Duplicate {
  private Object key;
  private List<UsageDecision> usages;
  
  public static class UsageDecision {
    private NameUsage usage;
    private EditorialDecision decision;
  
    public NameUsage getUsage() {
      return usage;
    }
  
    public void setUsage(NameUsage usage) {
      this.usage = usage;
    }
  
    public EditorialDecision getDecision() {
      return decision;
    }
  
    public void setDecision(EditorialDecision decision) {
      this.decision = decision;
    }
  
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      UsageDecision that = (UsageDecision) o;
      return Objects.equals(usage, that.usage) &&
          Objects.equals(decision, that.decision);
    }
  
    @Override
    public int hashCode() {
      return Objects.hash(usage, decision);
    }
  }
  
  public Object getKey() {
    return key;
  }
  
  public void setKey(Object key) {
    this.key = key;
  }
  
  public List<UsageDecision> getUsages() {
    return usages;
  }
  
  public void setUsages(List<UsageDecision> usages) {
    this.usages = usages;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    Duplicate duplicate = (Duplicate) o;
    return Objects.equals(key, duplicate.key) &&
        Objects.equals(usages, duplicate.usages);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(key, usages);
  }
}
