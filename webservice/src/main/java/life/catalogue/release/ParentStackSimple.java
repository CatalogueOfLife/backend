package life.catalogue.release;

import life.catalogue.api.model.SimpleName;

import org.gbif.nameparser.api.Rank;

import java.util.LinkedList;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ParentStackSimple {
  private static final Logger LOG = LoggerFactory.getLogger(ParentStackSimple.class);

  private final Consumer<SNC> sncConsumer;
  private final LinkedList<SNC> parents = new LinkedList<>();
  private String doubtfulUsageID = null;

  /**
   * @param sncConsumer function to be called when the usage is removed from the stack and has correct children and synonym counts.
   */
  public ParentStackSimple(@Nullable Consumer<SNC> sncConsumer) {
    this.sncConsumer = sncConsumer;
  }

  static class SNC {
    SimpleName sn;
    int children = 0;
    int synonyms = 0;

    public SNC(SimpleName nu) {
      sn = nu;
    }
  }

  public SimpleName find(Rank r) {
    for (SNC p : parents) {
      if (p.sn.getRank() == r) {
        return p.sn;
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

  public SimpleName secondLast() {
    return parents.isEmpty() ? null : parents.get(parents.size()-2).sn;
  }

  public SimpleName last() {
    return parents.isEmpty() ? null : parents.getLast().sn;
  }

  public void push(SimpleName nu) {
    if (parents.isEmpty()) {
      // the very first entry can point to a missing parent, e.g. when we iterate over subtrees only

    } else if (nu.getParent() == null) {
      // no parent, i.e. a new root!
      clear();

    } else {
      while (!parents.isEmpty()) {
        if (parents.getLast().sn.getId().equals(nu.getParent())) {
          // the last src usage on the parent stack represents the current parentKey, we are in good state!
          break;
        } else {
          // remove last parent until we find the real one
          var p = parents.removeLast();
          if (sncConsumer != null && p.sn.getStatus().isTaxon()) {
            sncConsumer.accept(p);
          }
          // reset doubtful marker if the taxon gets removed from the stack
          if (doubtfulUsageID != null && doubtfulUsageID.equals(p.sn.getId())) {
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
