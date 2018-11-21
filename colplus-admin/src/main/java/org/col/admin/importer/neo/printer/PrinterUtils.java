package org.col.admin.importer.neo.printer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import javax.annotation.Nullable;

import com.google.common.base.Function;
import com.google.common.io.Files;
import org.col.admin.importer.neo.DotProcessor;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.model.NeoProperties;
import org.col.admin.importer.neo.traverse.Traversals;
import org.col.admin.importer.neo.traverse.TreeWalker;
import org.gbif.nameparser.api.Rank;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

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
      return NeoProperties.getScientificNameWithAuthor(n, NeoProperties.NULL_NAME);
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
    BasePrinter printer;

    switch (format) {
      case GML:
        printer = new GmlPrinter(writer, lowestRank, true);
        break;

      case DOT:
        printer = new DotPrinter(writer, lowestRank);
        break;

      case LIST:
        printer = new ListPrinter(writer);
        break;

      case TAB:
        printer = new TabPrinter(writer);
        break;

      default:
        printer = new TxtPrinter(writer);
        break;
    }
    TreeWalker.walkTree(neo, Traversals.SORTED_TREE, root, lowestRank, printer);
    printer.close();
    writer.flush();
  }
  
  /**
   * Prints the entire neo4j graph out to a writer in no particular order.
   * It does not require a root node but dumps all nodes and relations.
   */
  public static void dumpDotFile(GraphDatabaseService neo, Writer writer) throws Exception {
    try (DotProcessor dot = new DotProcessor(writer);
         Transaction tx = neo.beginTx()
    ) {
      for (Node n : neo.getAllNodes()) {
        dot.accept(n);
      }
    }
    writer.flush();
  }
  
  public static Writer fileWriter(File f) throws IOException {
    Files.createParentDirs(f);
    return new FileWriter(f);
  }
}
