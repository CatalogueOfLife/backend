package org.col.api.search;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.col.api.model.NameUsage;
import org.col.api.model.VernacularName;
import org.col.api.vocab.Issue;

public class NameUsageWrapper<T extends NameUsage> {

  private T usage;
  private Set<Issue> issues;
  private List<VernacularName> vernacularNames;

  public NameUsageWrapper() {}

  public NameUsageWrapper(T usage) {
    this.usage = usage;
  }

  public T getUsage() {
    return usage;
  }

  public void setUsage(T usage) {
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

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    NameUsageWrapper<?> that = (NameUsageWrapper<?>) o;
    return Objects.equals(usage, that.usage) && Objects.equals(issues, that.issues)
        && Objects.equals(vernacularNames, that.vernacularNames);
  }

  @Override
  public int hashCode() {
    return Objects.hash(usage, issues, vernacularNames);
  }
}
