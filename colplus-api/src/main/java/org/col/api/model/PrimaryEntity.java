package org.col.api.model;

import org.col.api.vocab.Issue;

import java.util.Set;

/**
 *
 */
public interface PrimaryEntity {

  Integer getKey();

  Set<Issue> getIssues();

  void addIssue(Issue issue);
}
