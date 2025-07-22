package life.catalogue.api.model;

import life.catalogue.api.vocab.Issue;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

public interface IssueContainer {
  
  DevNull VOID = new DevNull();

  static IssueContainer simple() {
    return new Simple();
  }

  Set<Issue> getIssues();
  
  void setIssues(Set<Issue> issues);

  default void add(Issue... issues) {
    if (issues != null && issues.length > 0) {
      for (var iss : issues) {
        getIssues().add(iss);
      }
    }
  }

  default void add(IssueContainer issues) {
    getIssues().addAll(issues.getIssues());
  }

  default void add(Collection<Issue> issues) {
    getIssues().addAll(issues);
  }

  default boolean remove(Issue issue) {
    return getIssues().remove(issue);
  }

  default void clear() {
    getIssues().clear();
  }

  default boolean contains(Issue issue) {
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
    public boolean remove(Issue issue) {
      return issues.remove(issue);
    }

    @Override
    public boolean contains(Issue issue) {
      return issues.contains(issue);
    }

    @Override
    public String toString() {
      return StringUtils.join(issues, ",");
    }
  }

  /**
   * Simple hash map based issue container with an id value, e.g. for linking issues to names or name usages.
   */
  class SimpleWithID extends Simple {
    private String id;

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
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
    public void add(Issue... issues) {
      // ignore
    }

    @Override
    public void add(IssueContainer issues) {
      // ignore
    }

    @Override
    public void add(Collection<Issue> issues) {
      // ignore
    }

    @Override
    public void clear() {
      // ignore
    }

    @Override
    public boolean remove(Issue issue) {
      return false;
    }
  
    @Override
    public boolean contains(Issue issue) {
      return false;
    }
  }
  
}
