package life.catalogue.api.search;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.TaxGroup;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;

public class NameUsageWrapper extends SimpleNameClassification {

  private NameUsage usage;
  private Set<Issue> issues;
  // decisions about this usage in any number of project or releases
  private List<SimpleDecision> decisions;
  // mode of the sector
  private Sector.Mode sectorMode;
  // subject datasetKey of usage sector
  private Integer sectorDatasetKey;
  // publisher of the usage sectors subject dataset
  private UUID sectorPublisherKey;
  private Set<InfoGroup> secondarySourceGroups;
  private Set<Integer> secondarySourceKeys;
  private TaxGroup group;
  private List<SimpleVernacularName> vernacularNames;

  public NameUsageWrapper() {}

  /**
   * Creates a wrapper from a full NameUsage, converting it to SimpleName for the usage field
   * and storing the original as indexingUsage for ES field extraction.
   */
  public NameUsageWrapper(NameUsage usage) {
    this.usage = usage;
    super.setId(usage.getId());
  }

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

  public Sector.Mode getSectorMode() {
    return sectorMode;
  }

  public void setSectorMode(Sector.Mode sectorMode) {
    this.sectorMode = sectorMode;
  }

  public Integer getSectorDatasetKey() {
    return sectorDatasetKey;
  }

  public void setSectorDatasetKey(Integer sectorDatasetKey) {
    this.sectorDatasetKey = sectorDatasetKey;
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

  public List<SimpleVernacularName> getVernacularNames() {
    return vernacularNames;
  }

  public void setVernacularNames(List<SimpleVernacularName> vernacularNames) {
    this.vernacularNames = vernacularNames;
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
      Objects.equals(secondarySourceGroups, that.secondarySourceGroups) &&
      Objects.equals(secondarySourceKeys, that.secondarySourceKeys) &&
      group == that.group;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), usage, issues, decisions, sectorDatasetKey, sectorPublisherKey, secondarySourceGroups, secondarySourceKeys, group);
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
