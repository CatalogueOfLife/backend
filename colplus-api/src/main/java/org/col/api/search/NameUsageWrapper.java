package org.col.api.search;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.col.api.model.NameUsage;
import org.col.api.model.VernacularName;
import org.col.api.vocab.Issue;

public class NameUsageWrapper {

  private NameUsage usage;
  private Set<Issue> issues;
  private List<VernacularName> vernacularNames;
  private String higherTaxonTrail;
  private List<String> higherTaxonIds;

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

  /*
   * A concatenated version of higher taxon IDs, produced by MyBatis when processing taxa for indexing into Elasticsearch. If it's not
   * desirable that this method shows up in the API (this is after all an API class), we need another (non-API) class. The non-API class
   * only has the concatenated ID string, while this class only has the List of IDs. When indexing into Elasticsearch we use the new class
   * with the concatenated IDs. When querying Elasticsearch, we use this class.
   */
  @JsonIgnore // Never serve this up to the client
  public String getHigherTaxonTrail() {
    return higherTaxonTrail;
  }

  public void setHigherTaxonTrail(String higherTaxonTrail) {
    this.higherTaxonTrail = higherTaxonTrail;
  }

  public List<String> getHigherTaxonIds() {
    return higherTaxonIds;
  }

  public void setHigherTaxonIds(List<String> higherTaxonIds) {
    this.higherTaxonIds = higherTaxonIds;
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
