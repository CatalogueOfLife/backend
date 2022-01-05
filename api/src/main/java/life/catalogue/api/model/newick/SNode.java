package life.catalogue.api.model.newick;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Objects;

import org.catalogueoflife.newick.XNode;

/**
 * ScientificName Node with S (scientific name),ND (node identifier) and R (rank) keys.
 */
public class SNode extends XNode<SNode> {
  private String id;
  private Rank rank;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public Rank getRank() {
    return rank;
  }

  public void setRank(Rank rank) {
    this.rank = rank;
  }

  @Override
  public String getValue(String key) {
    switch (key) {
      case "S": return getLabel();
      case "R": return rank.name().toLowerCase();
      case "ND": return id;
    }
    return null;
  }

  @Override
  public void setValue(String key, String value) {
    switch (key) {
      case "S": setLabel(value); break;
      case "R": rank = value == null ? null : Rank.valueOf(value.toUpperCase()); break;
      case "ND": id = value; break;
    }
  }

  @Override
  protected List<String> listKeys() {
    return List.of("S","R","ND");
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SNode)) return false;
    if (!super.equals(o)) return false;
    SNode sNode = (SNode) o;
    return Objects.equals(id, sNode.id) && rank == sNode.rank;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), id, rank);
  }
}
