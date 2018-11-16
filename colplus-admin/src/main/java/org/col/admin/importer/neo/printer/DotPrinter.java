package org.col.admin.importer.neo.printer;

import java.io.IOException;
import java.io.Writer;
import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import org.col.admin.importer.neo.model.Labels;
import org.col.admin.importer.neo.model.RelType;
import org.col.admin.importer.neo.traverse.RankEvaluator;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
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
public class DotPrinter implements TreePrinter {
  private static final Joiner SEMI_JOINER = Joiner.on(';').skipNulls();
  private final Writer writer;
  private final Function<Node, String> getTitle;
  private final RankEvaluator rankEvaluator;
  private final boolean showLabels = false;
  
  public DotPrinter(Writer writer, @Nullable Rank rankThreshold, Function<Node, String> getTitle) {
    this.writer = writer;
    this.getTitle = getTitle;
    this.rankEvaluator = new RankEvaluator(rankThreshold);
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
  public void start(Node n) {
    try {
      writer.append("  n");
      writer.append(String.valueOf(n.getId()));
      writer.append("  [label=\"");
      if (showLabels) {
        writer.append(SEMI_JOINER.join(n.getLabels()));
        writer.append(": ");
      }
      writer.append(getTitle.apply(n));
      writer.append("\"");
      if (n.hasLabel(Labels.SYNONYM)) {
        writer.append(", fontcolor=darkgreen");
      }
      writer.append("]\n");

      // edges
      for (Relationship rel : n.getRelationships(Direction.OUTGOING)) {
        if (rankEvaluator.evaluateNode(rel.getOtherNode(n))) {
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

  @Override
  public void end(Node n) {

  }
}
