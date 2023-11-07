package life.catalogue.printer;

import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.model.newick.SNode;
import life.catalogue.dao.TaxonCounter;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.ibatis.session.SqlSessionFactory;
import org.catalogueoflife.newick.Node;
import org.catalogueoflife.newick.SimpleNode;

/**
 * Print an entire dataset in the extended Newick format, listing the name, rank and id in the extended properties.
 * <p>
 * A basic example tree would look like this:
 * <pre>
 * (((ADH2:0.1[&&NHX:S=human:E=1.1.1.1], ADH1:0.11[&&NHX:S=human:E=1.1.1.1]):0.05[&&NHX:S=Primates:E=1.1.1.1:D=Y:B=100], ADHY:0.1[&&NHX:S=nematode:E=1.1.1.1],ADHX:0.12[&&NHX:S=insect:E=1.1.1.1]):0.1[&&NHX:S=Metazoa:E=1.1.1.1:D=N],
 * </pre>
 */
public class NewickPrinter extends AbstractTreePrinter {
  private static final int MAX_NODES = 100000;
  private final List<Node<?>> roots = new ArrayList<>();
  private final LinkedList<Node> parents = new LinkedList<>();
  private boolean extended;

  public NewickPrinter(TreeTraversalParameter params, Set<Rank> ranks, Rank countRank, TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    super(params, ranks, countRank, taxonCounter, factory, writer);
    params.setSynonyms(false);
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
    Node<?> node = build(u);
    if (parents.isEmpty()) {
      roots.add(node);
    } else {
      parents.getLast().addChild(node);
    }
    parents.add(node);
  }

  private Node<?> build(SimpleName u) {
    Node<?> n = extended ? new SNode() : new SimpleNode();
    n.setLabel(u.getLabel());
    n.setLength(null);
    if (extended) {
      var xn = (SNode) n;
      xn.setId(u.getId());
      xn.setLabel(u.getLabel());
      xn.setRank(u.getRank());
    }
    return n;
  }

  protected void end(SimpleName u) {
    parents.removeLast();
  }

  @Override
  public void close() throws IOException {
    boolean first = true;
    for (var r : roots) {
      if (!first) {
        writer.append("\n\n");
      }
      r.printTree(writer);
      first = false;
    }
    writer.append("\n");
    super.close();
  }

}
