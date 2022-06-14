package life.catalogue.assembly;

import life.catalogue.api.model.Classification;
import life.catalogue.api.model.NameUsageBase;
import life.catalogue.assembly.TreeHandler.Usage;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Parent stack that expects breadth first iterations which needs to track more than a depth first one.
 */
public class ParentStack {
  private final NameUsageBase root;
  private NameUsageBase current;
  private final LinkedList<NameUsageBase> parents = new LinkedList<>();
  private String doubtfulUsageID = null;

  public ParentStack(NameUsageBase root) {
    this.root = root;
    parents.push(root);
  }

  /**
   * List the current classification
   */
  public List<NameUsageBase> classification() {
    return parents;
  }

  /**
   * @return the lowest matched parent to be used for newly created usages.
   */
  public NameUsageBase matchParent() {
    return null;
  }

  public void put(NameUsageBase nu) {
    if (nu.getParentId() == null) {
      // no parent, i.e. a new root!
      clear();
    } else {
      while (!parents.isEmpty()) {
        if (parents.getLast().getId().equals(nu.getParentId())) {
          // the last src usage on the parent stack represents the current parentKey, we are in good state!
          break;
        } else {
          // remove last parent until we find the real one
          NameUsageBase p = parents.removeLast();
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
    current = nu;
  }

  private void clear() {
    parents.clear();
    doubtfulUsageID = null;
  }
}
