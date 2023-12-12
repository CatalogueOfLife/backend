package life.catalogue.release;

import com.google.common.collect.ImmutableList;

import life.catalogue.api.model.NameUsageCore;

import org.gbif.nameparser.api.Rank;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
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
    T usage;
    int children = 0;
    int synonyms = 0;

    public SNC(T nu) {
      usage = nu;
    }

    @Override
    public String toString() {
      return usage + " #child=" + children + " #syn=" + synonyms;
    }
  }

  public T find(Rank r) {
    for (SNC<T> p : parents) {
      if (p.usage.getRank() == r) {
        return p.usage;
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
        if (doubtfulUsageID.equals(u.usage.getId())) {
          return u.usage;
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
      doubtfulUsageID = parents.getLast().usage.getId();
    }
  }

  public T secondLast() {
    return parents.size() >= 2 ? parents.get(parents.size()-2).usage : null;
  }

  public T last() {
    return parents.isEmpty() ? null : parents.getLast().usage;
  }

  public List<SNC<T>> getParents(boolean skipLast) {
    var ps = ImmutableList.copyOf(parents);
    if (skipLast && !ps.isEmpty()) {
      return ps.subList(0, parents.size()-1);
    }
    return ps;
  }

  public Optional<Rank> getLowestConcreteRank(boolean skipLast) {
    var iter = parents.descendingIterator();
    if (skipLast && iter.hasNext()) {
      iter.next(); // skip
    }
    while (iter.hasNext()) {
      var p = iter.next();
      if (!p.usage.getRank().isUncomparable()) {
        return Optional.of(p.usage.getRank());
      }
    }
    return Optional.empty();
  }

  public void push(T nu) {
    if (parents.isEmpty()) {
      // the very first entry can point to a missing parent, e.g. when we iterate over subtrees only

    } else if (nu.getParentId() == null) {
      // no parent, i.e. a new root!
      clear();

    } else {
      while (!parents.isEmpty()) {
        if (parents.getLast().usage.getId().equals(nu.getParentId())) {
          // the last src usage on the parent stack represents the current parentKey, we are in good state, keep adding!
          break;
        } else {
          // remove last parent until we find the real one
          var p = parents.removeLast();
          // reset doubtful marker if the taxon gets removed from the stack
          if (doubtfulUsageID != null && doubtfulUsageID.equals(p.usage.getId())) {
            doubtfulUsageID = null;
          }
          // notify stack user about removal of accepted name
          if (removeFunc != null && p.usage.getStatus().isTaxon()) {
            removeFunc.accept(p);
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
      pRank = parents.getLast().usage.getRank();
      if (nu.getStatus().isTaxon()) {
        parents.getLast().children++;
      } else {
        parents.getLast().synonyms++;
      }
    }
    parents.add(new SNC(nu));
    if (nu.getStatus() != null && nu.getStatus().isTaxon()
        && pRank != null && pRank.notOtherOrUnranked()
        && nu.getRank().higherOrEqualsTo(pRank)
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
