package org.col.importer.neo.printer;

import java.io.IOException;
import java.io.Writer;

import org.col.importer.neo.model.RankedUsage;
import org.col.common.io.TabWriter;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.Node;

/**
 * Dumps a normalizer db in a simple tab delimited format showing the following columns:
 * name, rank, status, family
 * Expects no pro parte relations in the walker!
 */
public class ListPrinter extends BasePrinter{
  private final TabWriter writer;
  private String family;

  public ListPrinter(Writer writer) {
    super(true);
    this.writer = new TabWriter(writer);
  }
  
  @Override
  public void close() {
  }
  
  @Override
  public void start(RankedUsage u) {
    try {
      if (Rank.FAMILY == u.rank) {
        family = u.name;

      } else if (Rank.FAMILY.higherThan(u.rank)) {
        String[] row = new String[4];

        row[0] = u.getNameWithAuthor();
        row[1] = u.rank.name().toLowerCase();
        row[2] = (u.isSynonym() ? "synonym" : "accepted");
        row[3] = family;
        writer.write(row);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  @Override
  public void end(Node n) {
  
  }
}
