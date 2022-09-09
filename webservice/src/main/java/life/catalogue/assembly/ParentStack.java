package life.catalogue.assembly;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.SimpleNameWithNidx;

import java.util.LinkedList;
import java.util.List;

/**
 * Parent stack that expects breadth first iterations which needs to track more than a depth first one.
 */
public class ParentStack {
  private final SimpleNameWithNidx root;
  private final LinkedList<MatchedUsage> parents = new LinkedList<>();
  private String doubtfulUsageID = null;
  private boolean first = true;

  /**
   * @param rootTarget the default attachment point to the target taxonomy
   */
  public ParentStack(SimpleNameWithNidx rootTarget) {
    this.root = rootTarget;
  }

  public static class MatchedUsage {
    final SimpleNameWithNidx usage;
    SimpleNameWithNidx match;

    public MatchedUsage(SimpleNameWithNidx usage) {
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
  public SimpleNameWithNidx lowestParentMatch() {
    var it = parents.descendingIterator();
    while(it.hasNext()){
      var nu = it.next();
      if (nu.match != null) {
        return nu.match;
      }
    }
    return root;
  }

  public MatchedUsage secondLast() {
    return parents.isEmpty() ? null : parents.get(parents.size()-2);
  }

  public MatchedUsage last() {
    return parents.isEmpty() ? null : parents.getLast();
  }

  public void put(SimpleNameWithNidx nu) {
    if (nu.getParent() == null) {
      // no parent, i.e. a new root!
      clear();

    } else {
      while (!parents.isEmpty()) {
        if (parents.getLast().usage.getId().equals(nu.getParent())) {
          // the last src usage on the parent stack represents the current parentKey, we are in good state!
          break;
        } else {
          // remove last parent until we find the real one
          var p = parents.removeLast().usage;
          // reset doubtful marker if the taxon gets removed from the stack
          if (doubtfulUsageID != null && doubtfulUsageID.equals(p.getId())) {
            doubtfulUsageID = null;
          }
        }
      }
      if (!first && parents.isEmpty()) {
        throw new IllegalStateException("Usage parent " + nu.getParent() + " not found for " + nu.getLabel());
      }
    }
    parents.add(new MatchedUsage(nu));
    if (first) {
      first = false;
    }
  }

  private void clear() {
    parents.clear();
    doubtfulUsageID = null;
  }

  public void setMatch(SimpleNameWithNidx match) {
    parents.getLast().match = match; // let it throw if we have a match but no parents - cant really happen
  }

  public int size() {
    return parents.size();
  }
}
