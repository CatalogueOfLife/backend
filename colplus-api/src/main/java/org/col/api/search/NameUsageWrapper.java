package org.col.api.search;

import java.util.*;

import org.col.api.model.EditorialDecision;
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
  private EditorialDecision decision;
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

  public List<SimpleName> getClassification() {
    return classification;
  }

  public void setClassification(List<SimpleName> classification) {
    this.classification = classification;
  }

  public void setClassificationIds(List<String> ids) {
    // NB last element is the taxon itself. Couldn't figure out the SQL to exclude it
    if (classification == null) {
      classification = new ArrayList<>(ids.size() - 1);
      SimpleName sn;
      for (int i = 0; i < ids.size() - 1; i++) {
        (sn = new SimpleName()).setId(ids.get(i));
        classification.add(sn);
      }
    } else {
      for (int i = 0; i < ids.size() - 1; i++) {
        classification.get(i).setId(ids.get(i));
      }
    }
  }

  public void setClassificationRanks(List<Rank> ranks) {
    if (classification == null) {
      classification = new ArrayList<>(ranks.size());
      SimpleName sn;
      for (int i = 0; i < ranks.size() - 1; i++) {
        (sn = new SimpleName()).setRank(ranks.get(i));
        classification.add(sn);
      }
    } else {
      for (int i = 0; i < ranks.size() - 1; i++) {
        classification.get(i).setRank(ranks.get(i));
      }
    }
  }

  public void setClassificationNames(List<String> names) {
    if (classification == null) {
      classification = new ArrayList<>(names.size());
      SimpleName sn;
      for (int i = 0; i < names.size() - 1; i++) {
        (sn = new SimpleName()).setName(names.get(i));
        classification.add(sn);
      }
    } else {
      for (int i = 0; i < names.size() - 1; i++) {
        classification.get(i).setName(names.get(i));
      }
    }
  }
  
  public EditorialDecision getDecision() {
    return decision;
  }
  
  public void setDecision(EditorialDecision decision) {
    this.decision = decision;
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
    NameUsageWrapper that = (NameUsageWrapper) o;
    return Objects.equals(usage, that.usage) &&
        Objects.equals(vernacularNames, that.vernacularNames) &&
        Objects.equals(classification, that.classification) &&
        Objects.equals(issues, that.issues) &&
        Objects.equals(decision, that.decision) &&
        Objects.equals(publisherKey, that.publisherKey);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(usage, vernacularNames, classification, issues, decision, publisherKey);
  }
}
