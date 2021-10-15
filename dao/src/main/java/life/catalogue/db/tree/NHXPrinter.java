package life.catalogue.db.tree;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.newick.NHXNode;
import life.catalogue.dao.TaxonCounter;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Print an entire dataset in the extended Newick format, listing the name, rank and id in the extended properties.
 * <p>
 * A basic example tree would look like this:
 * <pre>
 * (((ADH2:0.1[&&NHX:S=human:E=1.1.1.1], ADH1:0.11[&&NHX:S=human:E=1.1.1.1]):0.05[&&NHX:S=Primates:E=1.1.1.1:D=Y:B=100], ADHY:0.1[&&NHX:S=nematode:E=1.1.1.1],ADHX:0.12[&&NHX:S=insect:E=1.1.1.1]):0.1[&&NHX:S=Metazoa:E=1.1.1.1:D=N],
 * </pre>
 */
public class NHXPrinter extends AbstractTreePrinter {
  private static final int MAX_NODES = 10000;
  private final List<NHXNode> roots = new ArrayList<>();
  private final LinkedList<NHXNode> parents = new LinkedList<>();
  private boolean extended = false;
  /**
   * @param sectorKey optional sectorKey to restrict printed tree to
   */
  public NHXPrinter(int datasetKey, Integer sectorKey, String startID, boolean synonyms, Set<Rank> ranks, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    super(datasetKey, sectorKey, startID, false, ranks, countRank, taxonCounter, factory, writer);

  }

  /**
   * Prints extended information inside Newick comments.
   */
  public void useExtendedFormat() {
    this.extended = true;
  }

  protected void start(SimpleName u) throws IOException {
    if (taxonCount > MAX_NODES) {
      throw new IllegalArgumentException("Tree exceeds maximum of "+MAX_NODES+" nodes");
    }
    NHXNode node;
    if (parents.isEmpty()) {
      node = new NHXNode(u, null);
      roots.add(node);
    } else {
      node = new NHXNode(u, parents.getLast());
    }
    parents.add(node);
  }

  protected void end(SimpleName u) {
    parents.removeLast();
  }

  @Override
  protected void close() throws IOException {
    boolean first = true;
    for (var r : roots) {
      if (!first) {
        writer.append(",\n");
      }
      if (extended) {
        r.printExtended(writer);
      } else {
        r.print(writer);
      }
      first = false;
    }
    writer.append("\n");
    super.close();
  }

}
