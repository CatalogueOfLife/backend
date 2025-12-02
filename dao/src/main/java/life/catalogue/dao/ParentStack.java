package life.catalogue.dao;

import life.catalogue.api.model.NameUsageCore;

import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A stack of usages that will automatically remove usages from the stack according to the classification.
 * Simply push new usages in depth first order to the stack which will then update itself and notify optional start/end handlers
 * about the depth first traversal of the tree.
 * @param <T>
 */
public class ParentStack<T extends NameUsageCore> {
  private static final Logger LOG = LoggerFactory.getLogger(ParentStack.class);

  private final List<StackHandler<T>> handler = new ArrayList<>();

  private final LinkedList<SNC<T>> parents = new LinkedList<>();
  private String doubtfulUsageID = null;

  /**
   * An event handler interface that accepts a start and end event for taxa / accepted names in depth first iteration of a usage tree.
   * The parent stack can listen to these handlers and fire the events for a sorted postgres stream.
   */
  public interface StackHandler<XT> {
     void start(XT n);

    /**
     * @param n the wrapped usage with counts for direct, accepted children and number of synonyms.
     */
    void end(SNC<XT> n);
  }

  public static class SNC<T> {
    public T usage;
    public int children = 0;
    public int synonyms = 0;

    public SNC(T nu) {
      usage = nu;
    }

    @Override
    public String toString() {
      return usage + " #child=" + children + " #syn=" + synonyms;
    }
  }

  public void addHandler(StackHandler<T> handler) {
    this.handler.add(handler);
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

  public List<T> getParents() {
    return getParents(false);
  }

  public List<T> getParents(boolean skipLast) {
    if (parents.isEmpty()) return Collections.emptyList();
    return parents.stream()
      .skip(skipLast ? 1 : 0)
      .map(s -> s.usage)
      .collect(Collectors.toList());
  }


  /**
   * Get all parents above and including the given startID.
   * @param startID
   * @return empty list of sublist of parents starting with the startID
   */
  public List<T> getParents(String startID) {
    if (parents.isEmpty()) return Collections.emptyList();
    boolean started = false;
    List<T> parentsAbove = new ArrayList<>();
    for (var p : parents) {
      if (p.usage.getId().equals(startID)) {
        started=true;
      }
      if (started) {
        parentsAbove.add(p.usage);
      }
    }
    return parentsAbove;
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

  public T getByRank(Rank rank) {
    var iter = parents.descendingIterator();
    while (iter.hasNext()) {
      var p = iter.next();
      if (p.usage.getRank() == rank) {
        return p.usage;
      }
    }
    return null;
  }

  public T getByID(String id) {
    var iter = parents.descendingIterator();
    while (iter.hasNext()) {
      var p = iter.next();
      if (p.usage.getId().equals(id)) {
        return p.usage;
      }
    }
    return null;
  }

  /**
   * Flushes the remaining usages from the stack and passes them to the handlers.
   * Should be called at the end when all usages are pushed.
   */
  public void flush() {
    if (!handler.isEmpty()) {
      // notify callback
      while (!parents.isEmpty()) {
        var p = parents.removeLast();
        // notify handler about removal of accepted name
        if (p.usage.getStatus().isTaxon()) {
          handler.forEach(h -> h.end(p));
        }
      }
    } else {
      parents.clear();
    }
  }

  public <XT extends T> void push(XT nu) {
    if (parents.isEmpty()) {
      // the very first entry can point to a missing parent, e.g. when we iterate over subtrees only

    } else if (nu.getParentId() == null) {
      // no parent, i.e. a new root!
      doubtfulUsageID = null;
      flush();

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
          if (!handler.isEmpty() && p.usage.getStatus().isTaxon()) {
            handler.forEach(h -> h.end(p));
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
    // add to stack and notify handler
    handler.forEach(h -> h.start(nu));
    parents.add(new SNC<>(nu));
    if (nu.getStatus() != null && nu.getStatus().isTaxon()
        && pRank != null && pRank.notOtherOrUnranked()
        && nu.getRank().higherOrEqualsTo(pRank)
    ) {
      LOG.debug("Bad parent rank {}. Mark {} as doubtful", pRank, parents.getLast());
      markSubtreeAsDoubtful();
    }
  }

  public int depth() {
    return parents.size();
  }
}
