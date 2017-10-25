package org.col.commands.importer.neo.printer;

import com.google.common.base.Throwables;
import org.col.api.vocab.Rank;
import org.col.commands.importer.neo.model.Labels;
import org.col.commands.importer.neo.model.NeoProperties;
import org.col.commands.importer.neo.model.RelType;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Node;
import org.parboiled.common.StringUtils;

import java.io.IOException;
import java.io.Writer;

/**
 * A handler that can be used with the TaxonWalker to print a neo4j taxonomy in a simple nested text structure.
 */
public class TxtPrinter implements TreePrinter {
  public static final String SYNONYM_SYMBOL = "*";
  public static final String BASIONYM_SYMBOL = "$";

  private static final int indentation = 2;
  private int level = 0;
  private final Writer writer;
  private final boolean showIds;


  public TxtPrinter(Writer writer, boolean showIds) {
    this.writer = writer;
    this.showIds = showIds;
  }

  public TxtPrinter(Writer writer) {
    this(writer, false);
  }

  @Override
  public void start(Node n) {
    print(n);
    level++;
  }

  @Override
  public void end(Node n) {
    level--;
  }

  private void print(Node n) {
    try {
      //writer.write(String.valueOf(n.getId()));
      writer.write(StringUtils.repeat(' ', level * indentation));
      if (n.hasLabel(Labels.SYNONYM)) {
        writer.write(SYNONYM_SYMBOL);
      }
      if (n.hasRelationship(RelType.BASIONYM_OF, Direction.OUTGOING)) {
        writer.write(BASIONYM_SYMBOL);
      }
      writer.write(NeoProperties.getScientificName(n));
      String author = NeoProperties.getAuthorship(n);
      if (!org.apache.commons.lang3.StringUtils.isBlank(author)) {
        writer.write(" ");
        writer.write(author);
      }
      if (n.hasProperty(NeoProperties.RANK)) {
        writer.write(" [");
        writer.write(Rank.values()[(Integer) n.getProperty(NeoProperties.RANK)].name().toLowerCase());
        if (showIds) {
          writer.write(",");
          writer.write(String.valueOf(n.getId()));
        }
        writer.write("]");
      }
      writer.write("\n");

    } catch (IOException e) {
      Throwables.propagate(e);
    }
  }

  @Override
  public void close() {

  }
}
