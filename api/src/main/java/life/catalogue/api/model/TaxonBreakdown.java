package life.catalogue.api.model;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/**
 * The breakdown of a taxon's subtree for a chart: the taxon, its accepted descendants counted by rank, the rank every
 * node is counted at, the one or two ring ranks the tree below is built of, and that tree. A node without an id is the
 * remainder at its rank, i.e. the counted descendants of its parent that are not placed in any node of that ring.
 */
public class TaxonBreakdown {
  private SimpleName taxon;
  private Map<Rank, Integer> counts;
  private Rank countRank;
  private List<Rank> ranks = new ArrayList<>();
  private int total;
  private List<Node> children = new ArrayList<>();

  public static class Node {
    @JsonUnwrapped
    private SimpleName name;
    private int count;
    private List<Node> children = new ArrayList<>();

    public Node() {
    }

    public Node(SimpleName name, int count) {
      this.name = name;
      this.count = count;
    }

    /**
     * A remainder node: the parent's counted descendants not placed in any node of the given rank.
     */
    public static Node remainder(Rank rank, int count) {
      SimpleName sn = new SimpleName();
      sn.setRank(rank);
      return new Node(sn, count);
    }

    public SimpleName getName() {
      return name;
    }

    public void setName(SimpleName name) {
      this.name = name;
    }

    public int getCount() {
      return count;
    }

    public void setCount(int count) {
      this.count = count;
    }

    public List<Node> getChildren() {
      return children;
    }

    public void setChildren(List<Node> children) {
      this.children = children;
    }

    /**
     * True for a remainder node, which carries no id. Not serialized, a missing id says it.
     */
    @JsonIgnore
    public boolean isRemainder() {
      return name == null || name.getId() == null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Node node)) return false;
      return count == node.count && Objects.equals(name, node.name) && Objects.equals(children, node.children);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, count, children);
    }

    @Override
    public String toString() {
      return name + " count=" + count + " children=" + children.size();
    }
  }

  public SimpleName getTaxon() {
    return taxon;
  }

  public void setTaxon(SimpleName taxon) {
    this.taxon = taxon;
  }

  public Map<Rank, Integer> getCounts() {
    return counts;
  }

  public void setCounts(Map<Rank, Integer> counts) {
    this.counts = counts;
  }

  public Rank getCountRank() {
    return countRank;
  }

  public void setCountRank(Rank countRank) {
    this.countRank = countRank;
  }

  public List<Rank> getRanks() {
    return ranks;
  }

  public void setRanks(List<Rank> ranks) {
    this.ranks = ranks;
  }

  public int getTotal() {
    return total;
  }

  public void setTotal(int total) {
    this.total = total;
  }

  public List<Node> getChildren() {
    return children;
  }

  public void setChildren(List<Node> children) {
    this.children = children;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TaxonBreakdown that)) return false;
    return total == that.total
      && Objects.equals(taxon, that.taxon)
      && Objects.equals(counts, that.counts)
      && countRank == that.countRank
      && Objects.equals(ranks, that.ranks)
      && Objects.equals(children, that.children);
  }

  @Override
  public int hashCode() {
    return Objects.hash(taxon, counts, countRank, ranks, total, children);
  }
}
