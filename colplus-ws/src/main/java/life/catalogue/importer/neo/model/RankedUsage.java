package life.catalogue.importer.neo.model;

import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Node;

public class RankedUsage extends RankedName {
  public final Node usageNode;

  public RankedUsage(Node usageNode, Node nameNode, String name, String author, Rank rank) {
    super(nameNode, name, author, rank);
    this.usageNode = usageNode;
  }

  public int getId() {
    return (int) usageNode.getId();
  }
  
  public boolean isSynonym() {
    return usageNode.hasLabel(Labels.SYNONYM);
  }
}
