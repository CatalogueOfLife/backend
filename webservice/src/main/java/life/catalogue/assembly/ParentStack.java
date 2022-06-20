package life.catalogue.assembly;

import life.catalogue.api.model.NameUsageBase;

import java.util.LinkedList;
import java.util.List;

/**
 * Parent stack that expects breadth first iterations which needs to track more than a depth first one.
 */
public class ParentStack {
  private final NameUsageBase root;
  private final LinkedList<MatchedUsage> parents = new LinkedList<>();
  private String doubtfulUsageID = null;

  /**
   * @param rootTarget the default attachment point to the target taxonomy
   */
  public ParentStack(NameUsageBase rootTarget) {
    this.root = rootTarget;
  }

  public static class MatchedUsage {
    final NameUsageBase usage;
    NameUsageBase match;

    public MatchedUsage(NameUsageBase usage) {
      this.usage = usage;
    }
  }

  /**
   * List the current classification
   */
  public List<MatchedUsage> classification() {
    return parents;
  }

  public boolean isDoubtful() {
    return doubtfulUsageID != null;
  }

  /**
   * Sets the doubtful flag for the current usage and all its descendants.
   */
  public void markSubtreeAsDoubtful() {
    if (!parents.isEmpty() && doubtfulUsageID == null) {
      doubtfulUsageID = parents.getLast().usage.getId();
    }
  }

  /**
   * @return the lowest matched parent to be used for newly created usages.
   */
  public NameUsageBase lowestParentMatch() {
    for (var mu : parents) {
      if (mu.match != null) {
        return mu.match;
      }
    }
    return root;
  }

  public NameUsageBase lowest() {
    return parents.isEmpty() ? null : parents.getLast().usage;
  }

  public void put(NameUsageBase nu) {
    if (nu.getParentId() == null) {
      // no parent, i.e. a new root!
      clear();

    } else {
      while (!parents.isEmpty()) {
        if (parents.getLast().usage.getId().equals(nu.getParentId())) {
          // the last src usage on the parent stack represents the current parentKey, we are in good state!
          break;
        } else {
          // remove last parent until we find the real one
          NameUsageBase p = parents.removeLast().usage;
          // reset doubtful marker if the taxon gets removed from the stack
          if (doubtfulUsageID != null && doubtfulUsageID.equals(p.getId())) {
            doubtfulUsageID = null;
          }
        }
      }
      if (parents.isEmpty()) {
        throw new IllegalStateException("Usage parent " + nu.getParentId() + " not found for " + nu.getLabel());
      }
    }
    parents.add(new MatchedUsage(nu));
  }

  private void clear() {
    parents.clear();
    doubtfulUsageID = null;
  }

  public void setMatch(NameUsageBase match) {
    parents.getLast().match = match; // let it throw if we have a match but no parents - cant really happen
  }

  public int size() {
    return parents.size();
  }
}
