package org.col.api.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.col.api.model.NameUsage;
import org.col.api.model.VernacularName;
import org.col.api.vocab.Issue;
import org.gbif.nameparser.api.Rank;

public class NameUsageWrapper {

  public static class HigherTaxon {
    private String id;
    private Rank rank;
    private String scientificName;

    public String getId() {
      return id;
    }

    public Rank getRank() {
      return rank;
    }

    public String getScientificName() {
      return scientificName;
    }
  }

  private NameUsage usage;
  private Set<Issue> issues;
  private List<VernacularName> vernacularNames;
  private List<HigherTaxon> higherTaxa;

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

  public List<HigherTaxon> getHigherTaxa() {
    return higherTaxa;
  }

  public void setHigherTaxonIds(List<String> ids) {
    if (higherTaxa == null) {
      higherTaxa = new ArrayList<>(ids.size());
      for (int i = 0; i < ids.size(); i++) {
        HigherTaxon ht = new HigherTaxon();
        ht.id = ids.get(i);
        higherTaxa.add(ht);
      }
    } else {
      for (int i = 0; i < ids.size(); i++) {
        higherTaxa.get(i).id = ids.get(i);
      }
    }
  }

  public void setHigherTaxonRanks(List<Rank> ranks) {
    if (higherTaxa == null) {
      higherTaxa = new ArrayList<>(ranks.size());
      for (int i = 0; i < ranks.size(); i++) {
        HigherTaxon ht = new HigherTaxon();
        ht.rank = ranks.get(i);
        higherTaxa.add(ht);
      }
    } else {
      for (int i = 0; i < ranks.size(); i++) {
        higherTaxa.get(i).rank = ranks.get(i);
      }
    }
  }

  public void setHigherTaxonNames(List<String> names) {
    if (higherTaxa == null) {
      higherTaxa = new ArrayList<>(names.size());
      for (int i = 0; i < names.size(); i++) {
        HigherTaxon ht = new HigherTaxon();
        ht.scientificName = names.get(i);
        higherTaxa.add(ht);
      }
    } else {
      for (int i = 0; i < names.size(); i++) {
        higherTaxa.get(i).scientificName = names.get(i);
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
    return Objects.equals(usage, that.usage) && Objects.equals(issues, that.issues)
        && Objects.equals(vernacularNames, that.vernacularNames);
  }

  @Override
  public int hashCode() {
    return Objects.hash(usage, issues, vernacularNames);
  }
}
