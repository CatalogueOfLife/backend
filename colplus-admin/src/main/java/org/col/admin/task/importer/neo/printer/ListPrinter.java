package org.col.admin.task.importer.neo.printer;

import java.io.IOException;
import java.io.Writer;

import com.google.common.base.Function;
import com.google.common.base.Throwables;
import org.col.admin.task.importer.neo.model.Labels;
import org.col.admin.task.importer.neo.model.NeoProperties;
import org.gbif.io.TabWriter;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Node;

/**
 * Dumps a normalizer db in a simple tab delimited format showing the following columns:
 * name, rank, status, family
 * Expects no pro parte relations in the walker!
 */
public class ListPrinter implements TreePrinter {
  private final Function<Node, String> getTitle;
  private final TabWriter writer;
  private String family;

  public ListPrinter(Writer writer, Function<Node, String> getTitle) {
    this.writer = new TabWriter(writer);
    this.getTitle = getTitle;
  }

  @Override
  public void close() {
  }

  @Override
  public void start(Node n) {
    try {
      Rank rank = NeoProperties.getRank(n, Rank.UNRANKED);
      if (Rank.FAMILY == rank) {
        family = NeoProperties.getScientificName(n);

      } else if (Rank.FAMILY.higherThan(rank)) {
        String[] row = new String[4];

        row[0] = getTitle.apply(n);
        row[1] = rank.name().toLowerCase();
        row[2] = (n.hasLabel(Labels.SYNONYM) ? "synonym" : "accepted");
        row[3] = family;
        writer.write(row);
      }
    } catch (IOException e) {
      Throwables.propagate(e);
    }
  }

  @Override
  public void end(Node n) {

  }
}
