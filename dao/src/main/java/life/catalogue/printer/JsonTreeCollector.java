package life.catalogue.printer;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.dao.ParentStack;
import life.catalogue.dao.TaxonCounter;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSessionFactory;

/**
 * Collects the entire dataset in a nested object tree (actually a forrest).
 */
public class JsonTreeCollector extends AbstractTreePrinter {
  private final LinkedList<TreeName> root = new LinkedList<>();
  private final LinkedList<TreeName> tree = new LinkedList<>();

  public JsonTreeCollector(TreeTraversalParameter params, Set<Rank> ranks, @Nullable Boolean extinct, @Nullable Rank countRank, @Nullable TaxonCounter taxonCounter, SqlSessionFactory factory, Writer writer) {
    super(params, ranks, extinct, countRank, taxonCounter, factory, writer);
  }

  public LinkedList<TreeName> getRoot() {
    return root;
  }

  public static class TreeName {
    public final SimpleName name;
    public int count;
    public final List<TreeName> synonyms = new ArrayList<>();
    public final List<TreeName> children = new ArrayList<>();

    public TreeName(SimpleName name) {
      this.name = name;
    }
  }

  protected void start(SimpleName u) throws IOException {
    final var pid = u.getParent(); // we need to set this back at the end, otherwise the tree printer gets mad at us
    u.setParent(null);
    var tn = new TreeName(u);
    if (tree.isEmpty()) {
      root.add(tn);
    } else {
      if (u.isSynonym()) {
        tree.getLast().synonyms.add(tn);
      } else {
        tree.getLast().children.add(tn);
      }
    }
    tree.add(tn);
    if (countRank != null) {
      tn.count = taxonCount;
    }
    u.setParent(pid);
  }

  protected void end(SimpleName u) throws IOException {
    var p = tree.removeLast();
    if (!p.name.getId().equals(u.getId())) {
      throw new IllegalStateException("TreePrinter ended with wrong parent");
    }
  }

}
