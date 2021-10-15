package life.catalogue.db.tree;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.dao.TaxonCounter;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.nameparser.api.Rank;

import javax.annotation.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Print to the graphviz dot format
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
public class DotPrinter extends AbstractTreePrinter {
  private static final String SYN_SYMBOL = "syn";
  private static final String SYN_COLOR = "darkgreen";

  public DotPrinter(int datasetKey, Integer sectorKey, String startID, boolean synonyms, Set<Rank> ranks, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    super(datasetKey, sectorKey, startID, synonyms, ranks, countRank, taxonCounter, factory, writer);
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
  public void close() throws IOException {
    writer.write("}\n");
    writer.flush();
  }


  protected void start(SimpleName u) throws IOException {
    writer.append("  n");
    writer.append(u.getId());
    writer.append("  [label=\"");
    writer.append(u.getLabel());
    writer.append("\"");
    String type = null;
    if (u.getStatus().isSynonym()) {
      writer.append(", fontcolor="+SYN_COLOR);
      type = SYN_SYMBOL;
    }
    writer.append("]\n");

    // edges
    if (u.getParent() != null) {
      writer.append("  n");
      writer.append(u.getParent());
      writer.append(" -> n");
      writer.append(u.getId());
      if (type != null) {
        writer.append("  [color="+SYN_COLOR+", fontcolor="+SYN_COLOR+", label=");
        writer.append(type);
        writer.append("]");
      }
      writer.append("\n");
    }

    //TODO: basionym rels
    //type = "bas";
  }

  protected void end(SimpleName u) {
    //nothing
  }

}
