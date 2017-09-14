package org.col.commands.importer.neo.printer;

import com.google.common.base.Function;
import org.col.api.vocab.Rank;
import org.col.commands.importer.neo.NeoDb;
import org.col.commands.importer.neo.model.NeoProperties;
import org.col.commands.importer.neo.traverse.TreeWalker;
import org.neo4j.graphdb.Node;

import javax.annotation.Nullable;
import java.io.Writer;

/**
 * Printing utilities for {@link org.col.commands.importer.neo.NeoDb}s
 * accepting different styles via {@link TreePrinter} implementations
 */
public class PrinterUtils {

  private PrinterUtils() {

  }

  private static final Function<Node, String> getCanonical = new Function<Node, String>() {
    @Nullable
    @Override
    public String apply(@Nullable Node n) {
      return NeoProperties.getCanonicalName(n);
    }
  };

  private static final Function<Node, String> getScientific = new Function<Node, String>() {
    @Nullable
    @Override
    public String apply(@Nullable Node n) {
      return NeoProperties.getScientificName(n);
    }
  };

  /**
   * Prints the entire neo4j tree out to a print stream, mainly for debugging.
   * Synonyms are marked with a prepended asterisk.
   */
  public static void printTree(NeoDb neo, Writer writer, GraphFormat format) throws Exception {
    printTree(neo, writer, format, true, null, null);
  }

  /**
   * Prints the entire neo4j tree out to a print stream, mainly for debugging.
   * Synonyms are marked with a prepended asterisk.
   */
  public static void printTree(NeoDb neo, Writer writer, GraphFormat format, final boolean fullNames, @Nullable Rank lowestRank, @Nullable Node root) throws Exception {
    TreePrinter printer;
    boolean includeProParte = false;
    switch (format) {
      case GML:
        printer = new GmlPrinter(writer, lowestRank, fullNames ? getScientific : getCanonical, true);
        break;

      case DOT:
        printer = new DotPrinter(writer, lowestRank, fullNames ? getScientific : getCanonical);
        break;

      case LIST:
        printer = new ListPrinter(writer, fullNames ? getScientific : getCanonical);
        break;

      case TAB:
        printer = new TabPrinter(writer, fullNames ? getScientific : getCanonical);
        break;

      case XML:
        printer = new XmlPrinter(writer);
        includeProParte = true;
        break;

      default:
        printer = new TxtPrinter(writer, fullNames ? getScientific : getCanonical);
        includeProParte = true;
        break;
    }
    TreeWalker.walkTree(neo.getNeo(), includeProParte, root, lowestRank, null, printer);
    printer.close();
    writer.flush();
  }

}
