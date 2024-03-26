package life.catalogue.api.search;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.vocab.Issue;

import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;

import life.catalogue.api.vocab.TaxGroup;

public class NameUsageWrapper extends SimpleNameClassification {

  private NameUsage usage;
  private Set<Issue> issues;
  // decisions about this usage in any number of project or releases
  private List<SimpleDecision> decisions;
  private Integer sectorDatasetKey;
  private UUID sectorPublisherKey;
  private UUID publisherKey;
  private Set<InfoGroup> secondarySourceGroups;
  private Set<Integer> secondarySourceKeys;
  private TaxGroup group;

  @Override
  public void setId(String id) {
    super.setId(id);
    if (usage != null) {
      usage.setId(id);
    }
  }

  public Set<Issue> getIssues() {
    return issues;
  }

  public void setIssues(Set<Issue> issues) {
    this.issues = issues;
  }

  public NameUsageWrapper() {}

  public NameUsageWrapper(NameUsage usage) {
    this.usage = usage;
    this.setId(usage.getId());
  }

  public NameUsage getUsage() {
    return usage;
  }

  public void setUsage(NameUsage usage) {
    this.usage = usage;
  }

  public List<SimpleDecision> getDecisions() {
    return decisions;
  }

  public void setDecisions(List<SimpleDecision> decisions) {
    this.decisions = decisions;
  }

  public Integer getSectorDatasetKey() {
    return sectorDatasetKey;
  }

  public void setSectorDatasetKey(Integer sectorDatasetKey) {
    this.sectorDatasetKey = sectorDatasetKey;
  }

  public UUID getPublisherKey() {
    return publisherKey;
  }

  public void setPublisherKey(UUID publisherKey) {
    this.publisherKey = publisherKey;
  }

  public UUID getSectorPublisherKey() {
    return sectorPublisherKey;
  }

  public void setSectorPublisherKey(UUID sectorPublisherKey) {
    this.sectorPublisherKey = sectorPublisherKey;
  }

  public Set<InfoGroup> getSecondarySourceGroups() {
    return secondarySourceGroups;
  }

  public void setSecondarySourceGroups(Set<InfoGroup> secondarySourceGroups) {
    this.secondarySourceGroups = secondarySourceGroups;
  }

  public Set<Integer> getSecondarySourceKeys() {
    return secondarySourceKeys;
  }

  public void setSecondarySourceKeys(Set<Integer> secondarySourceKeys) {
    this.secondarySourceKeys = secondarySourceKeys;
  }

  public TaxGroup getGroup() {
    return group;
  }

  public void setGroup(TaxGroup group) {
    this.group = group;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    NameUsageWrapper that = (NameUsageWrapper) o;
    return Objects.equals(usage, that.usage) &&
      Objects.equals(issues, that.issues) &&
      Objects.equals(decisions, that.decisions) &&
      Objects.equals(sectorDatasetKey, that.sectorDatasetKey) &&
      Objects.equals(sectorPublisherKey, that.sectorPublisherKey) &&
      Objects.equals(publisherKey, that.publisherKey) &&
      Objects.equals(secondarySourceGroups, that.secondarySourceGroups) &&
      Objects.equals(secondarySourceKeys, that.secondarySourceKeys) &&
      group == that.group;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), usage, issues, decisions, sectorDatasetKey, sectorPublisherKey, publisherKey, secondarySourceGroups, secondarySourceKeys, group);
  }

  @Override
  public String toString() {
    try {
      return ApiModule.MAPPER.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
