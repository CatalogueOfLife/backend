package org.col.api.model;

import java.util.List;
import java.util.Objects;

public class Duplicate {
  private String key;
  private List<UsageDecision> usages;
  
  public static class Mybatis {
    private String key;
    private List<String> usages;
  
    public void setKey(String key) {
      this.key = key;
    }
  
    public void setUsages(List<String> usages) {
      this.usages = usages;
    }
  
    public String getKey() {
      return key;
    }
  
    public List<String> getUsages() {
      return usages;
    }
  
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Mybatis mybatis = (Mybatis) o;
      return Objects.equals(key, mybatis.key) &&
          Objects.equals(usages, mybatis.usages);
    }
  
    @Override
    public int hashCode() {
      return Objects.hash(key, usages);
    }
  }

  public static class UsageDecision {
    private NameUsage usage;
    private List<SimpleName> classification;
    private EditorialDecision decision;
  
    public NameUsage getUsage() {
      return usage;
    }
  
    public void setUsage(NameUsage usage) {
      this.usage = usage;
    }
  
    public List<SimpleName> getClassification() {
      return classification;
    }
  
    public void setClassification(List<SimpleName> classification) {
      this.classification = classification;
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
          Objects.equals(classification, that.classification) &&
          Objects.equals(decision, that.decision);
    }
  
    @Override
    public int hashCode() {
      return Objects.hash(usage, classification, decision);
    }
  }
  
  public String getKey() {
    return key;
  }
  
  public void setKey(String key) {
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
