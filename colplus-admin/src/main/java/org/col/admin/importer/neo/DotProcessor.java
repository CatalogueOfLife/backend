package org.col.admin.importer.neo;

import java.io.IOException;
import java.io.Writer;
import java.util.function.Consumer;

import com.google.common.base.Joiner;
import org.col.admin.importer.neo.model.Labels;
import org.col.admin.importer.neo.model.NeoProperties;
import org.col.admin.importer.neo.model.RelType;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

/**
 * Dumps every Node and relation from neo into a Dot graphviz file.
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
public class DotProcessor implements Consumer<Node>, AutoCloseable {
  private static final Joiner SEMI_JOINER = Joiner.on(';').skipNulls();
  private final Writer writer;
  
  public DotProcessor(Writer writer) {
    this.writer = writer;
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
  public void close() throws Exception {
    writer.write("}\n");
  }

  @Override
  public void accept(Node n) {
    try {
      writer.append("  n");
      writer.append(String.valueOf(n.getId()));
      writer.append("  [label=\"");
      if (n.hasLabel(Labels.NAME)) {
        writer.append(NeoProperties.getRank(n, Rank.UNRANKED).name());
        writer.append(" ");
        writer.append(NeoProperties.getScientificNameWithAuthor(n));
      } else {
        writer.append(SEMI_JOINER.join(n.getLabels()));
      }
      writer.append("\"");
      
      if (n.hasLabel(Labels.NAME)) {
        writer.append(", fontcolor=darkgreen");
      } else if (n.hasLabel(Labels.SYNONYM)) {
        writer.append(", fontcolor=blue");
      }
      writer.append("]\n");

      // edges
      for (Relationship rel : n.getRelationships(Direction.OUTGOING)) {
        //n1 -> n16 [type=parent_of]
        long start = rel.getStartNode().getId();
        long end = rel.getEndNode().getId();
        String type = null;
        if (rel.isType(RelType.PARENT_OF) || rel.isType(RelType.HAS_NAME)) {
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
          writer.append("  [color=");
          if (rel.isType(RelType.SYNONYM_OF)) {
            writer.append("blue, fontcolor=blue");
          } else {
            writer.append("darkgreen, fontcolor=darkgreen");
          }
          writer.append(", label=\"");
          writer.append(type);
          writer.append("\"]");
        }
        writer.append("\n");
      }

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
}
