package life.catalogue.api.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.NameUsage;
import life.catalogue.api.model.SimpleNameClassification;
import life.catalogue.api.vocab.Issue;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class NameUsageWrapper extends SimpleNameClassification {

  private NameUsage usage;
  private Set<Issue> issues;
  private List<SimpleDecision> decisions;
  private Integer sectorDatasetKey;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NameUsageWrapper)) return false;
    if (!super.equals(o)) return false;
    NameUsageWrapper that = (NameUsageWrapper) o;
    return Objects.equals(usage, that.usage) &&
      Objects.equals(issues, that.issues) &&
      Objects.equals(decisions, that.decisions) &&
      Objects.equals(sectorDatasetKey, that.sectorDatasetKey) &&
      Objects.equals(publisherKey, that.publisherKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), usage, issues, decisions, sectorDatasetKey, publisherKey);
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
