package org.col.api.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.col.api.model.NameUsage;
import org.col.api.model.SimpleName;
import org.col.api.model.VernacularName;
import org.col.api.vocab.Issue;
import org.gbif.nameparser.api.Rank;

public class NameUsageWrapper {

  private NameUsage usage;
  private SimpleVerbatimRecord verbatimRecord;
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

  public SimpleVerbatimRecord getVerbatimRecord() {
    return verbatimRecord;
  }

  public void setVerbatimRecord(SimpleVerbatimRecord verbatimRecord) {
    this.verbatimRecord = verbatimRecord;
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

  /*
   * TODO remove getter/setter for issues. They are just here so existing code won't break
   */
  @JsonIgnore
  public Set<Issue> getIssues() {
    if (verbatimRecord == null || verbatimRecord.getIssues().isEmpty()) {
      return null;
    }
    return verbatimRecord.getIssues();
  }

  public void setIssues(Set<Issue> issues) {
    if (issues == null || issues.isEmpty()) {
      verbatimRecord = null;
    } else {
      if (verbatimRecord == null) {
        verbatimRecord = new SimpleVerbatimRecord();
      }
      verbatimRecord.setIssues(issues);
    }
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

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    NameUsageWrapper that = (NameUsageWrapper) o;
    return Objects.equals(usage, that.usage) &&
        Objects.equals(verbatimRecord, that.verbatimRecord) &&
        Objects.equals(vernacularNames, that.vernacularNames) &&
        Objects.equals(classification, that.classification);
  }

  @Override
  public int hashCode() {
    return Objects.hash(usage, verbatimRecord, vernacularNames, classification);
  }
}
