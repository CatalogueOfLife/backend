package org.col.api.search;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.col.api.model.NameUsage;
import org.col.api.model.SimpleNameClassification;
import org.col.api.model.VernacularName;
import org.col.api.vocab.Issue;

public class NameUsageWrapper extends SimpleNameClassification {

  private NameUsage usage;
  private List<VernacularName> vernacularNames;
  private Set<Issue> issues;
  private Integer decisionKey;
  private UUID publisherKey;
  
  public Set<Issue> getIssues() {
    return issues;
  }
  
  public void setIssues(Set<Issue> issues) {
    this.issues = issues;
  }

  public NameUsageWrapper() {}

  public NameUsageWrapper(NameUsage usage) {
    this.usage = usage;
  }

  public NameUsage getUsage() {
    return usage;
  }

  public void setUsage(NameUsage usage) {
    this.usage = usage;
  }
  
  public List<VernacularName> getVernacularNames() {
    return vernacularNames;
  }

  public void setVernacularNames(List<VernacularName> vernacularNames) {
    this.vernacularNames = vernacularNames;
  }
  
  public Integer getDecisionKey() {
    return decisionKey;
  }
  
  public void setDecisionKey(Integer decisionKey) {
    this.decisionKey = decisionKey;
  }
  
  public UUID getPublisherKey() {
    return publisherKey;
  }
  
  public void setPublisherKey(UUID publisherKey) {
    this.publisherKey = publisherKey;
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    NameUsageWrapper that = (NameUsageWrapper) o;
    return Objects.equals(usage, that.usage) &&
        Objects.equals(vernacularNames, that.vernacularNames) &&
        Objects.equals(issues, that.issues) &&
        Objects.equals(decisionKey, that.decisionKey) &&
        Objects.equals(publisherKey, that.publisherKey);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), usage, vernacularNames, issues, decisionKey, publisherKey);
  }
}
