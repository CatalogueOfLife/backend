package org.col.api.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.col.api.model.NameUsage;
import org.col.api.model.SimpleName;
import org.col.api.model.VernacularName;
import org.col.api.vocab.Issue;
import org.gbif.nameparser.api.Rank;

public class NameUsageWrapper {
  
  private NameUsage usage;
  private Set<Issue> issues;
  private List<VernacularName> vernacularNames;
  private List<SimpleName> classification;

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

  public Set<Issue> getIssues() {
    return issues;
  }

  public void setIssues(Set<Issue> issues) {
    this.issues = issues;
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
    if (classification == null) {
      classification = new ArrayList<>(ids.size());
      for (int i = 0; i < ids.size(); i++) {
        SimpleName ht = new SimpleName();
        ht.setId(ids.get(i));
        classification.add(ht);
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
      for (int i = 0; i < ranks.size(); i++) {
        SimpleName ht = new SimpleName();
        ht.setRank(ranks.get(i));
        classification.add(ht);
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
      for (int i = 0; i < names.size(); i++) {
        SimpleName ht = new SimpleName();
        ht.setName(names.get(i));
        classification.add(ht);
      }
    } else {
      for (int i = 0; i < names.size(); i++) {
        classification.get(i).setName(names.get(i));
      }
    }
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    NameUsageWrapper that = (NameUsageWrapper) o;
    return Objects.equals(usage, that.usage) &&
        Objects.equals(issues, that.issues) &&
        Objects.equals(vernacularNames, that.vernacularNames) &&
        Objects.equals(classification, that.classification);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(usage, issues, vernacularNames, classification);
  }
}
