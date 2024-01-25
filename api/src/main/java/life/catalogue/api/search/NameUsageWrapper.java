package life.catalogue.api.search;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.vocab.Issue;

import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;

public class NameUsageWrapper extends SimpleNameClassification {

  private NameUsage usage;
  private Set<Issue> issues;
  private List<SimpleDecision> decisions;
  private Integer sectorDatasetKey;
  private UUID sectorPublisherKey;
  private Sector.Mode sectorMode;
  private UUID publisherKey;
  private Map<InfoGroup, Integer> secondarySources = new EnumMap<>(InfoGroup.class);

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

  public Sector.Mode getSectorMode() {
    return sectorMode;
  }

  public void setSectorMode(Sector.Mode sectorMode) {
    this.sectorMode = sectorMode;
  }

  public Map<InfoGroup, Integer> getSecondarySources() {
    return secondarySources;
  }

  public void setSecondarySources(Map<InfoGroup, Integer> secondarySources) {
    this.secondarySources = secondarySources;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    NameUsageWrapper that = (NameUsageWrapper) o;
    return Objects.equals(usage, that.usage) && Objects.equals(issues, that.issues) && Objects.equals(decisions, that.decisions) && Objects.equals(sectorDatasetKey, that.sectorDatasetKey) && Objects.equals(sectorPublisherKey, that.sectorPublisherKey) && sectorMode == that.sectorMode && Objects.equals(publisherKey, that.publisherKey) && Objects.equals(secondarySources, that.secondarySources);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), usage, issues, decisions, sectorDatasetKey, sectorPublisherKey, sectorMode, publisherKey, secondarySources);
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
