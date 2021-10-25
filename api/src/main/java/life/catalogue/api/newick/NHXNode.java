package life.catalogue.api.newick;

import life.catalogue.api.model.SimpleName;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class NHXNode {
  private static final float NULL = Float.MIN_VALUE;
  private static final Pattern RESERVED = Pattern.compile("[()\\[\\],:;\\s]");
  private static final String REPLACEMENT = "_";

  private final String id;
  private final String label;
  private final Rank rank;
  private final float length;
  private final NHXNode parent;
  private final List<NHXNode> children = new ArrayList<>();

  public NHXNode(NHXNode parent) {
    this(null, parent);
  }
  public NHXNode(SimpleName u, NHXNode parent) {
    this.id = u.getId();
    this.label = u.getName().replaceAll("[^a-zA-Z]+", "_");
    this.rank = u.getRank();
    this.length = NULL;
    this.parent = parent;
    if (parent != null) {
      parent.children.add(this);
    }
  }

  public boolean hasLength() {
    return length != NULL;
  }


  /**
   * Prints the tree in simple Newick format.
   */
  public void print(Writer w) throws IOException {
    print(w, false);
  }

  /**
   * Prints the tree in Newick eXtended format.
   */
  public void printExtended(Writer w) throws IOException {
    print(w, true);
  }

  private void print(Writer w, boolean extended) throws IOException {
    if (!children.isEmpty()) {
      w.append("(");
      boolean first = true;
      for (var c : children) {
        if (!first) {
          w.append(",");
        }
        c.print(w, extended);
        first=false;
      }
      w.append(")");
    }
    w.append(repl(label));
    if (hasLength()) {
      w.append(":");
      w.append(String.valueOf(length));
    }
    if (extended) {
      w.append("[&&NHX:S=");
      w.append(repl(label));
      w.append(":ND=");
      w.append(repl(id));
      if (rank != null && rank.notOtherOrUnranked()) {
        w.append(":R=");
        w.append(rank.name().toLowerCase());
      }
      w.append("]");
    }
  }

  private static String repl(String x) {
    return x == null ? null : RESERVED.matcher(x).replaceAll(REPLACEMENT);
  }
}
