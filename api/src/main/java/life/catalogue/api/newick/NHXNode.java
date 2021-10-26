package life.catalogue.api.newick;

import com.google.common.annotations.VisibleForTesting;

import life.catalogue.api.model.SimpleName;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class NHXNode {
  private static final float NULL = Float.MIN_VALUE;
  private static final Pattern WHITESPACE = Pattern.compile("\\s");
  private static final String WS_REPLACEMENT = "_";
  private static final Pattern QUOTE = Pattern.compile("'");
  private static final Pattern RESERVED = Pattern.compile("[()\\[\\],:;']");

  private final String id;
  private final String label;
  private final Rank rank;
  private final float length;
  private final List<NHXNode> children = new ArrayList<>();

  public NHXNode(SimpleName u, NHXNode parent) {
    this.id = u.getId();
    this.label = u.getName().replaceAll("[^a-zA-Z]+", "_");
    this.rank = u.getRank();
    this.length = NULL;
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

  @VisibleForTesting
  protected static String repl(String x) {
    // need for quoting?
    if (RESERVED.matcher(x).find()) {
      return "'" + QUOTE.matcher(x).replaceAll("''") + "'";

    } else {
      var ws = WHITESPACE.matcher(x);
      if (ws.find()) {
        return ws.replaceAll(WS_REPLACEMENT);
      }
    }
    return x;
  }
}
