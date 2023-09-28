package life.catalogue.api.model;

import java.util.List;
import java.util.Objects;

import it.unimi.dsi.fastutil.ints.IntSet;

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

  public static class IntKeys {
    private String name;
    private IntSet keys;

    public void setName(String name) {
      this.name = name;
    }

    public void setKeys(IntSet keys) {
      this.keys = keys;
    }

    public String getName() {
      return name;
    }

    public IntSet getKeys() {
      return keys;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof IntKeys)) return false;
      IntKeys intKey = (IntKeys) o;
      return Objects.equals(name, intKey.name) && Objects.equals(keys, intKey.keys);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, keys);
    }
  }

  public static class UsageDecision {
    private NameUsage usage;
    private String sourceId;
    private Integer sourceDatasetKey;
    private List<SimpleName> classification;
    private EditorialDecision decision;
  
    public NameUsage getUsage() {
      return usage;
    }
  
    public void setUsage(NameUsage usage) {
      this.usage = usage;
    }

    public String getSourceId() {
      return sourceId;
    }

    public void setSourceId(String sourceId) {
      this.sourceId = sourceId;
    }

    public Integer getSourceDatasetKey() {
      return sourceDatasetKey;
    }

    public void setSourceDatasetKey(Integer sourceDatasetKey) {
      this.sourceDatasetKey = sourceDatasetKey;
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
      if (!(o instanceof UsageDecision)) return false;
      UsageDecision that = (UsageDecision) o;
      return Objects.equals(usage, that.usage)
             && Objects.equals(sourceId, that.sourceId)
             && Objects.equals(sourceDatasetKey, that.sourceDatasetKey)
             && Objects.equals(classification, that.classification)
             && Objects.equals(decision, that.decision);
    }

    @Override
    public int hashCode() {
      return Objects.hash(usage, sourceId, sourceDatasetKey, classification, decision);
    }

    @Override
    public String toString() {
      return usage == null ? "null" : usage.toString();
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
