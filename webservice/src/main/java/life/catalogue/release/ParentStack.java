package life.catalogue.release;

import life.catalogue.api.model.NameUsageCore;

import org.gbif.nameparser.api.Rank;

import java.util.LinkedList;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParentStack<T extends NameUsageCore> {
  private static final Logger LOG = LoggerFactory.getLogger(ParentStack.class);

  private final Consumer<SNC<T>> removeFunc;
  private final LinkedList<SNC<T>> parents = new LinkedList<>();
  private String doubtfulUsageID = null;

  /**
   * @param removeFunc function to be called when the usage is removed from the stack and has correct children and synonym counts.
   */
  public ParentStack(@Nullable Consumer<SNC<T>> removeFunc) {
    this.removeFunc = removeFunc;
  }

  static class SNC<T> {
    T sn;
    int children = 0;
    int synonyms = 0;

    public SNC(T nu) {
      sn = nu;
    }
  }

  public T find(Rank r) {
    for (SNC<T> p : parents) {
      if (p.sn.getRank() == r) {
        return p.sn;
      }
    }
    return null;
  }

  public boolean isDoubtful() {
    return doubtfulUsageID != null;
  }

  public T getDoubtful() {
    if (doubtfulUsageID != null) {
      for (var u : parents) {
        if (doubtfulUsageID.equals(u.sn.getId())) {
          return u.sn;
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
      doubtfulUsageID = parents.getLast().sn.getId();
    }
  }

  public T secondLast() {
    return parents.isEmpty() ? null : parents.get(parents.size()-2).sn;
  }

  public T last() {
    return parents.isEmpty() ? null : parents.getLast().sn;
  }

  public void push(T nu) {
    if (parents.isEmpty()) {
      // the very first entry can point to a missing parent, e.g. when we iterate over subtrees only

    } else if (nu.getParentId() == null) {
      // no parent, i.e. a new root!
      clear();

    } else {
      while (!parents.isEmpty()) {
        if (parents.getLast().sn.getId().equals(nu.getParentId())) {
          // the last src usage on the parent stack represents the current parentKey, we are in good state!
          break;
        } else {
          // remove last parent until we find the real one
          var p = parents.removeLast();
          if (removeFunc != null && p.sn.getStatus().isTaxon()) {
            removeFunc.accept(p);
          }
          // reset doubtful marker if the taxon gets removed from the stack
          if (doubtfulUsageID != null && doubtfulUsageID.equals(p.sn.getId())) {
            doubtfulUsageID = null;
          }
        }
      }
      if (parents.isEmpty()) {
        throw new IllegalStateException("Usage parent " + nu.getParentId() + " not found for " + nu.getId());
      }
    }
    // if the classification ordering is wrong, mark it as doubtful
    Rank pRank = null;
    if (!parents.isEmpty()) {
      pRank = parents.getLast().sn.getRank();
      if (nu.getStatus().isTaxon()) {
        parents.getLast().children++;
      } else {
        parents.getLast().synonyms++;
      }
    }
    parents.add(new SNC(nu));
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
