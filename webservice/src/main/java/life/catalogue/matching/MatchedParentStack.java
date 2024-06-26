package life.catalogue.matching;

import life.catalogue.api.model.SimpleNameWithNidx;

import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parent stack that expects breadth first iterations which needs to track more than a depth first one.
 */
public class MatchedParentStack {
  private static final Logger LOG = LoggerFactory.getLogger(MatchedParentStack.class);
  private SimpleNameWithNidx root;
  private final LinkedList<MatchedUsage> parents = new LinkedList<>();
  private String doubtfulUsageID = null;
  private boolean first = true;

  /**
   * @param rootTarget the default attachment point to the target taxonomy
   */
  public MatchedParentStack(SimpleNameWithNidx rootTarget) {
    LOG.info("Create parent stack with root {}", rootTarget);
    this.root = rootTarget;
  }

  /**
   * Changes the default attachment point to the target taxonomy
   */
  public void setRoot(SimpleNameWithNidx root) {
    LOG.info("Change root of parent stack to {}", root);
    this.root = root;
  }

  public boolean containsMatch(String id) {
    for (var p : parents) {
      if (p.match != null && p.match.getId().equals(id)) {
        return true;
      }
    }
    return false;
  }

  public static class MatchedUsage {
    public final SimpleNameWithNidx usage;
    public SimpleNameWithNidx match;

    public MatchedUsage(SimpleNameWithNidx usage) {
      this.usage = usage;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof MatchedUsage)) return false;
      MatchedUsage that = (MatchedUsage) o;
      return Objects.equals(usage, that.usage) && Objects.equals(match, that.match);
    }

    @Override
    public int hashCode() {
      return Objects.hash(usage, match);
    }

    @Override
    public String toString() {
      return usage + "; match=" + match;
    }
  }

  /**
   * List the current classification
   */
  public List<MatchedUsage> classification() {
    return parents;
  }

  public String classificationToString() {
    StringBuilder sb = new StringBuilder();
    for (var p : parents) {
      if (sb.length() > 0) sb.append(" | ");
      sb.append(p.usage.getRank());
      sb.append(" ");
      sb.append(p.usage.getLabel());
      if (p.match != null) {
        sb.append("*");
      }
    }
    return sb.toString();
  }

  public boolean hasRoot() {
    return root != null;
  }

  public boolean isDoubtful() {
    return doubtfulUsageID != null;
  }

  public MatchedUsage getDoubtful() {
    if (doubtfulUsageID != null) {
      for (var u : parents) {
        if (doubtfulUsageID.equals(u.usage.getId())) {
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
      doubtfulUsageID = parents.getLast().usage.getId();
    }
  }

  /**
   * @return the lowest matched parent to be used for newly created usages or the root taxon if no parents exist.
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

  /**
   * Filters parents so that only those remain which have a non null match property and which are not listed in the optional exclusion list.
   * @return
   *
   * @param excludeIDs optional list of ids to be excluded from the result list
   */
  public LinkedList<MatchedUsage> matchedParentsOnly(String... excludeIDs) {
    Set<String> exclusion = new HashSet<>();
    if (excludeIDs != null) {
      exclusion.addAll(Arrays.asList(excludeIDs));
    }
    return parents.stream().filter(u -> u.match != null && !exclusion.contains(u.match.getId())).collect(Collectors.toCollection(LinkedList::new));
  }

  public MatchedUsage secondLast() {
    return parents.isEmpty() ? null : parents.get(parents.size()-2);
  }

  public MatchedUsage last() {
    return parents.isEmpty() ? null : parents.getLast();
  }

  public void push(SimpleNameWithNidx nu) {
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
    // if the classification ordering is wrong, mark it as doubtful
    Rank pRank = null;
    if (!parents.isEmpty()) {
      pRank = parents.getLast().usage.getRank();
    }
    parents.add(new MatchedUsage(nu));
    if (first) {
      first = false;
    }
    if (nu.getStatus() != null && nu.getStatus().isTaxon()
        && pRank != null && nu.getRank().higherThan(pRank)
        && nu.getRank().notOtherOrUnranked() && pRank.notOtherOrUnranked()
    ) {
      LOG.debug("Bad parent rank {}. Mark {} as doubtful", pRank, parents.getLast().usage);
      markSubtreeAsDoubtful();
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
