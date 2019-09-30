package org.col.api.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.col.api.model.NameUsage;
import org.col.api.model.SimpleName;
import org.col.api.model.VernacularName;
import org.col.api.vocab.Issue;
import org.gbif.nameparser.api.Rank;

public class NameUsageWrapper {

  private NameUsage usage;
  private List<VernacularName> vernacularNames;
  private List<SimpleName> classification;
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

  /**
   * The entire classification for the usage starting with the highest root and including the taxon or synonym itself as
   * the last entry in the list
   */
  public List<SimpleName> getClassification() {
    return classification;
  }

  public void setClassification(List<SimpleName> classification) {
    this.classification = classification;
  }

  public void setClassificationIds(List<String> ids) {
    // NB last element is the taxon itself which should be included:
    // https://github.com/Sp2000/colplus-backend/issues/326
    if (classification == null) {
      classification = new ArrayList<>(ids.size());
      SimpleName sn;
      for (int i = 0; i < ids.size(); i++) {
        (sn = new SimpleName()).setId(ids.get(i));
        classification.add(sn);
      }
    } else {
      for (int i = 0; i < ids.size(); i++) {
        classification.get(i).setId(ids.get(i));
      }
    }
  }

  public void setClassificationRanks(List<Rank> ranks) {
    if (classification == null) {
      classification = new ArrayList<>(ranks.size());
      SimpleName sn;
      for (int i = 0; i < ranks.size(); i++) {
        (sn = new SimpleName()).setRank(ranks.get(i));
        classification.add(sn);
      }
    } else {
      for (int i = 0; i < ranks.size(); i++) {
        classification.get(i).setRank(ranks.get(i));
      }
    }
  }

  public void setClassificationNames(List<String> names) {
    if (classification == null) {
      classification = new ArrayList<>(names.size());
      SimpleName sn;
      for (int i = 0; i < names.size(); i++) {
        (sn = new SimpleName()).setName(names.get(i));
        classification.add(sn);
      }
    } else {
      for (int i = 0; i < names.size(); i++) {
        classification.get(i).setName(names.get(i));
      }
    }
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
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    NameUsageWrapper that = (NameUsageWrapper) o;
    return Objects.equals(usage, that.usage) &&
        Objects.equals(vernacularNames, that.vernacularNames) &&
        Objects.equals(classification, that.classification) &&
        Objects.equals(issues, that.issues) &&
        Objects.equals(decisionKey, that.decisionKey) &&
        Objects.equals(publisherKey, that.publisherKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(usage, vernacularNames, classification, issues, decisionKey, publisherKey);
  }
}
