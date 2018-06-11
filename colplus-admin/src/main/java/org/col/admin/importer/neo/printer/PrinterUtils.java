package org.col.admin.importer.neo.printer;

import java.io.Writer;
import javax.annotation.Nullable;

import com.google.common.base.Function;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.model.NeoProperties;
import org.col.admin.importer.neo.traverse.Traversals;
import org.col.admin.importer.neo.traverse.TreeWalker;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;

/**
 * Printing utilities for {@link NeoDb}s
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

  private static final Function<Node, String> getScientificWithAuthorshipAndID = new Function<Node, String>() {
    @Nullable
    @Override
    public String apply(@Nullable Node n) {
      StringBuilder sb = new StringBuilder();
      sb.append(NeoProperties.getScientificNameWithAuthor(n));
      String id = NeoProperties.getID(n);
      if (id != null) {
        sb.append(" [").append(id).append("]");
      }
      return sb.toString();
    }
  };

  /**
   * Prints the entire neo4j tree out to a print stream, mainly for debugging.
   * Synonyms are marked with a prepended asterisk.
   */
  public static void printTree(GraphDatabaseService neo, Writer writer, GraphFormat format) throws Exception {
    printTree(neo, writer, format, false);
  }

  public static void printTree(GraphDatabaseService neo, Writer writer, GraphFormat format, boolean showNodeIds) throws Exception {
    printTree(neo, writer, format, null, null, showNodeIds);
  }

  /**
   * Prints the entire neo4j tree out to a print stream, mainly for debugging.
   * Synonyms are marked with a prepended asterisk.
   */
  public static void printTree(GraphDatabaseService neo, Writer writer, GraphFormat format, @Nullable Rank lowestRank, @Nullable Node root, boolean showNodeIds) throws Exception {
    TreePrinter printer;
    Function<Node, String> func = showNodeIds ? getScientificWithAuthorshipAndID : getScientificWithAuthorship;

    switch (format) {
      case GML:
        printer = new GmlPrinter(writer, lowestRank, func, true);
        break;

      case DOT:
        printer = new DotPrinter(writer, lowestRank, func);
        break;

      case LIST:
        printer = new ListPrinter(writer, func);
        break;

      case TAB:
        printer = new TabPrinter(writer, func);
        break;

      default:
        printer = new TxtPrinter(writer, showNodeIds);
        break;
    }
    TreeWalker.walkTree(neo, Traversals.SORTED_TREE, root, lowestRank, printer);
    printer.close();
    writer.flush();
  }

}
