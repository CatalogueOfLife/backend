package org.col.commands.importer.neo.printer;

import com.google.common.base.Function;
import org.col.api.vocab.Rank;
import org.col.commands.importer.neo.model.NeoProperties;
import org.col.commands.importer.neo.traverse.Traversals;
import org.col.commands.importer.neo.traverse.TreeWalker;
import org.neo4j.graphdb.GraphDatabaseService;
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

  private static final Function<Node, String> getScientificWithAuthorship = new Function<Node, String>() {
    @Nullable
    @Override
    public String apply(@Nullable Node n) {
      return NeoProperties.getScientificNameWithAuthor(n);
    }
  };

  /**
   * Prints the entire neo4j tree out to a print stream, mainly for debugging.
   * Synonyms are marked with a prepended asterisk.
   */
  public static void printTree(GraphDatabaseService neo, Writer writer, GraphFormat format) throws Exception {
    printTree(neo, writer, format, null, null);
  }

  /**
   * Prints the entire neo4j tree out to a print stream, mainly for debugging.
   * Synonyms are marked with a prepended asterisk.
   */
  public static void printTree(GraphDatabaseService neo, Writer writer, GraphFormat format, @Nullable Rank lowestRank, @Nullable Node root) throws Exception {
    TreePrinter printer;
    switch (format) {
      case GML:
        printer = new GmlPrinter(writer, lowestRank, getScientificWithAuthorship, true);
        break;

      case DOT:
        printer = new DotPrinter(writer, lowestRank, getScientificWithAuthorship);
        break;

      case LIST:
        printer = new ListPrinter(writer, getScientificWithAuthorship);
        break;

      case TAB:
        printer = new TabPrinter(writer, getScientificWithAuthorship);
        break;

      case XML:
        printer = new XmlPrinter(writer);
        break;

      default:
        printer = new TxtPrinter(writer);
        break;
    }
    TreeWalker.walkTree(neo, Traversals.SORTED_TREE, root, lowestRank, printer);
    printer.close();
    writer.flush();
  }

}
