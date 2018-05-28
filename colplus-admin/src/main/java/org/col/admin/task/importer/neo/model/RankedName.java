package org.col.admin.task.importer.neo.model;

import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Node;

public class RankedName {
  public final Node node;
  public final String name;
  public final String author;
  public final Rank rank;

  public RankedName(Node n, String name, String author, Rank rank) {
    this.node = n;
    this.name = name;
    this.author = author;
    this.rank = rank;
  }

  public int getId() {
    return (int) node.getId();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(name);
    if (author != null) {
      sb.append(' ').append(author);
    }
    if (rank != null) {
      sb.append(" [").append(rank.name().toLowerCase()).append(']');
    }
    return sb.toString();
  }
}
