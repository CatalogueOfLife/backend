package org.col.api.model;

import java.util.Collections;
import java.util.Set;

import org.col.api.vocab.Issue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface IssueContainer {
  
  DevNull VOID = new DevNull();
  
  Set<Issue> getIssues();
  
  void setIssues(Set<Issue> issues);
  
  void addIssue(Issue issue);
  
  boolean removeIssue(Issue issue);
  
  boolean hasIssue(Issue issue);
  
  default boolean hasIssues() {
    return !getIssues().isEmpty();
  }
  
  /**
   * Reusable issue container that does not store anything.
   */
  class DevNull implements IssueContainer {
    
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
    public boolean removeIssue(Issue issue) {
      return false;
    }
  
    @Override
    public boolean hasIssue(Issue issue) {
      return false;
    }
  }
  
  /**
   * Reusable issue container that does not store anything but logs added issues.
   */
  class DevNullLogging implements IssueContainer {
    private static final Logger LOG = LoggerFactory.getLogger(IssueContainer.class);
    private final String context;
  
    public static DevNullLogging dataset(int datasetKey) {
      return new DevNullLogging("Dataset " + datasetKey);
    }

    public DevNullLogging(String context) {
      this.context = context;
    }

    @Override
    public Set<Issue> getIssues() {
      return Collections.EMPTY_SET;
    }
    
    private void log(Issue issue) {
      LOG.debug("Added issue {} to {}", issue, context);
    }
    
    @Override
    public void setIssues(Set<Issue> issues) {
      for (Issue iss : issues) {
        log(iss);
      }
    }
    
    @Override
    public void addIssue(Issue issue) {
      log(issue);
    }
    
    @Override
    public boolean removeIssue(Issue issue) {
      return false;
    }
    
    @Override
    public boolean hasIssue(Issue issue) {
      return false;
    }
  }
  
}
