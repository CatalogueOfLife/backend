package life.catalogue.api.model;

import life.catalogue.api.vocab.Issue;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface IssueContainer {
  
  DevNull VOID = new DevNull();

  static IssueContainer simple() {
    return new Simple();
  }

  Set<Issue> getIssues();
  
  void setIssues(Set<Issue> issues);

  default void addIssue(Issue issue) {
    getIssues().add(issue);
  }

  default void addIssues(Issue... issues) {
    addIssues(Arrays.asList(issues));
  }

  default void addIssues(Collection<Issue> issues) {
    getIssues().addAll(issues);
  }

  default boolean removeIssue(Issue issue) {
    return getIssues().remove(issue);
  }

  default void clear() {
    getIssues().clear();
  }

  default boolean hasIssue(Issue issue) {
    return getIssues().contains(issue);
  }
  
  default boolean hasIssues() {
    return !getIssues().isEmpty();
  }

  /**
   * Simple hash map based issue container.
   */
  class Simple implements IssueContainer {
    private Set<Issue> issues = new HashSet<>();

    @Override
    public Set<Issue> getIssues() {
      return issues;
    }

    @Override
    public void setIssues(Set<Issue> issues) {
      this.issues.clear();
      this.issues.addAll(issues);
    }

    @Override
    public void addIssue(Issue issue) {
      issues.add(issue);
    }

    @Override
    public boolean removeIssue(Issue issue) {
      return issues.remove(issue);
    }

    @Override
    public boolean hasIssue(Issue issue) {
      return issues.contains(issue);
    }
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
