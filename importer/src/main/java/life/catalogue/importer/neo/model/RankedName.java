package life.catalogue.importer.neo.model;

import org.gbif.nameparser.api.Rank;

import org.neo4j.graphdb.Node;

public class RankedName {
  public final Node nameNode;
  public final String name;
  public final String author;
  public final String sensu;
  public final Rank rank;
  
  public RankedName(NeoName nn) {
    this.nameNode = nn.node;
    this.name = nn.getName().getScientificName();
    this.author = nn.getName().getAuthorship();
    this.sensu = nn.pnu.getTaxonomicNote();
    this.rank = nn.getName().getRank();
  }
  
  public RankedName(Node n, String name, String author, Rank rank) {
    this.nameNode = n;
    this.name = name;
    this.author = author;
    this.sensu = null;
    this.rank = rank;
  }
  
  public int getId() {
    return (int) nameNode.getId();
  }

  public String getNameWithAuthor() {
    return author == null ? name : name + " " + author;
  }
  
  @Override
  public String toString() {
    return toStringBuilder().toString();
  }
  
  public String toStringWithID() {
    StringBuilder sb = toStringBuilder();
    sb.append(" {");
    sb.append(getId());
    sb.append("}");
    return sb.toString();
  }
  
  public StringBuilder toStringBuilder() {
    StringBuilder sb = new StringBuilder();
    sb.append(name);
    if (author != null) {
      sb.append(' ').append(author);
    }
    if (rank != null) {
      sb.append(" [").append(rank.name().toLowerCase()).append(']');
    }
    return sb;
  }
}
