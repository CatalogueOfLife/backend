package life.catalogue.release;

import life.catalogue.api.model.SimpleName;

import org.gbif.nameparser.api.Rank;

import java.util.LinkedList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParentStackSimple {
  private static final Logger LOG = LoggerFactory.getLogger(ParentStackSimple.class);

  private final LinkedList<SimpleName> parents = new LinkedList<>();
  private String doubtfulUsageID = null;

  public List<SimpleName> classification() {
    return parents;
  }

  public SimpleName find(Rank r) {
    for (SimpleName p : parents) {
      if (p.getRank() == r) {
        return p;
      }
    }
    return null;
  }

  public boolean isDoubtful() {
    return doubtfulUsageID != null;
  }

  public SimpleName getDoubtful() {
    if (doubtfulUsageID != null) {
      for (var u : parents) {
        if (doubtfulUsageID.equals(u.getId())) {
          return u;
        }
      }
    }
    return null;
  }

  /**
   * Sets the doubtful flag for the current usage and all its descendants.
   */
  public void markSubtreeAsDoubtful() {
    if (!parents.isEmpty() && doubtfulUsageID == null) {
      doubtfulUsageID = parents.getLast().getId();
    }
  }

  public SimpleName secondLast() {
    return parents.isEmpty() ? null : parents.get(parents.size()-2);
  }

  public SimpleName last() {
    return parents.isEmpty() ? null : parents.getLast();
  }

  public void push(SimpleName nu) {
    if (parents.isEmpty()) {
      // the very first entry can point to a missing parent, e.g. when we iterate over subtrees only

    } else if (nu.getParent() == null) {
      // no parent, i.e. a new root!
      clear();

    } else {
      while (!parents.isEmpty()) {
        if (parents.getLast().getId().equals(nu.getParent())) {
          // the last src usage on the parent stack represents the current parentKey, we are in good state!
          break;
        } else {
          // remove last parent until we find the real one
          var p = parents.removeLast();
          // reset doubtful marker if the taxon gets removed from the stack
          if (doubtfulUsageID != null && doubtfulUsageID.equals(p.getId())) {
            doubtfulUsageID = null;
          }
        }
      }
      if (parents.isEmpty()) {
        throw new IllegalStateException("Usage parent " + nu.getParent() + " not found for " + nu.getLabel());
      }
    }
    // if the classification ordering is wrong, mark it as doubtful
    Rank pRank = null;
    if (!parents.isEmpty()) {
      pRank = parents.getLast().getRank();
    }
    parents.add(nu);
    if (nu.getStatus() != null && nu.getStatus().isTaxon()
        && pRank != null && nu.getRank().higherThan(pRank)
        && !nu.getRank().isAmbiguous() && !pRank.isAmbiguous()
    ) {
      LOG.debug("Bad parent rank {}. Mark {} as doubtful", pRank, parents.getLast());
      markSubtreeAsDoubtful();
    }
  }

  private void clear() {
    parents.clear();
    doubtfulUsageID = null;
  }

  public int size() {
    return parents.size();
  }
}
