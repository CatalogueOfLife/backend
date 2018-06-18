package org.col.api.model;

import java.util.Collections;
import java.util.Set;

import org.col.api.vocab.Issue;

public interface IssueContainer {

  DevNull VOID = new DevNull();

  Set<Issue> getIssues();

  void setIssues(Set<Issue> issues);

  void addIssue(Issue issue);

  boolean hasIssue(Issue issue);

  /**
   * Reusable issue container that does not store anything.
   */
  class DevNull implements IssueContainer{

    @Override
    public Set<Issue> getIssues() {
      return Collections.EMPTY_SET;
    }

    @Override
    public void setIssues(Set<Issue> issues) {
      // ignore
    }

    @Override
    public void addIssue(Issue issue) {
      // ignore
    }

    @Override
    public boolean hasIssue(Issue issue) {
      return false;
    }
  }

}
