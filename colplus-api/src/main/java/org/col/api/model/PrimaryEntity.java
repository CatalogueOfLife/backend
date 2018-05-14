package org.col.api.model;

import java.util.Set;

import org.col.api.vocab.Issue;

/**
 *
 */
public interface PrimaryEntity {

  Integer getKey();

  Set<Issue> getIssues();

  void addIssue(Issue issue);
}
