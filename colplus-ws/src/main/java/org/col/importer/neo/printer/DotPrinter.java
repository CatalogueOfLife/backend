package org.col.importer.neo.printer;

import java.io.IOException;
import java.io.Writer;
import javax.annotation.Nullable;

import org.col.importer.neo.model.Labels;
import org.col.importer.neo.model.RankedUsage;
import org.col.importer.neo.model.RelType;
import org.col.importer.neo.traverse.UsageRankEvaluator;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Relationship;

/**
 * Expects no pro parte relations in the walker!
 * http://www.graphviz.org/doc/info/lang.html
 * <p>
 * Example:
 * <p>
 * digraph G {
 * t1 [label="Animalia"]
 * t1 -> t16 [type=parent_of];
 * t16 [label="Abies"]
 * t17 [label="Pinus"]
 * t17 -> t16 [type=synonym_of];
 * }
 */
public class DotPrinter extends BasePrinter {
  private final Writer writer;
  private final UsageRankEvaluator rankEvaluator;
  
  public DotPrinter(Writer writer, @Nullable Rank rankThreshold) {
    super(true);
    this.writer = writer;
    this.rankEvaluator = new UsageRankEvaluator(rankThreshold);
    printHeader();
  }
  
  private void printHeader() {
    try {
      writer.write("digraph G {\n");
      writer.write("  node [shape=plaintext]\n\n");
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  @Override
  public void close() {
    try {
      writer.write("}\n");
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  @Override
  public void start(RankedUsage u) {
    try {
      writer.append("  n");
      writer.append(String.valueOf(u.getId()));
      writer.append("  [label=\"");
      writer.append(u.getNameWithAuthor());
      writer.append("\"");
      if (u.usageNode.hasLabel(Labels.SYNONYM)) {
        writer.append(", fontcolor=darkgreen");
      }
      writer.append("]\n");
      
      // edges
      for (Relationship rel : u.usageNode.getRelationships(Direction.OUTGOING)) {
        if (rankEvaluator.evaluateNode(rel.getOtherNode(u.usageNode))) {
          //n1 -> n16 [type=parent_of]
          long start = rel.getStartNode().getId();
          long end = rel.getEndNode().getId();
          String type = null;
          if (rel.isType(RelType.PARENT_OF)) {
            type = null;
          } else if (rel.isType(RelType.SYNONYM_OF)) {
            type = "acc";
          } else if (rel.isType(RelType.HAS_BASIONYM)) {
            type = "bas";
          } else {
            type = rel.getType().name().toLowerCase().replace("_of", "");
          }
          writer.append("  n");
          writer.append(String.valueOf(start));
          writer.append(" -> n");
          writer.append(String.valueOf(end));
          if (type != null) {
            writer.append("  [color=darkgreen, fontcolor=darkgreen, label=");
            writer.append(type);
            writer.append("]");
          }
          writer.append("\n");
        }
      }
      
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
