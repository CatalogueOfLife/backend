package org.col.db.mapper.model;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import org.col.api.model.NameUsage;
import org.col.api.vocab.Issue;

public class IssueWrapper<T extends NameUsage> {
  
  private T usage;
  private Set<Issue> issues;
  
  public IssueWrapper() {
  }
  
  public IssueWrapper(T usage) {
    this.usage = usage;
    this.issues = EnumSet.noneOf(Issue.class);
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
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IssueWrapper<?> that = (IssueWrapper<?>) o;
    return Objects.equals(usage, that.usage) &&
        Objects.equals(issues, that.issues);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(usage, issues);
  }
}
