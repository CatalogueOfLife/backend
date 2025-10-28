package life.catalogue.feedback;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class TokenTree {
  public static class TNode {
    private final Map<String, TNode> tokens = new HashMap<>();

    private void add(String[] tokens) {
      if (tokens.length == 1) {
        this.tokens.put(tokens[0], new StopNode());
      } else {
        var subtokens = Arrays.copyOfRange(tokens, 1, tokens.length);
        if (this.tokens.containsKey(tokens[0])) {
          TNode node = this.tokens.get(tokens[0]);
          if (node instanceof StopNode) {
            // we ignore further tokens as this short one is already considered a match!
          } else {
            node.add(subtokens);
          }
        } else {
          this.tokens.put(tokens[0], new TNode(subtokens));
        }
      }
    }
    public boolean contains(String token) {
      return this.tokens.containsKey(token);
    }
    public TNode get(String token) {
      return this.tokens.get(token);
    }
    public boolean isTerminal() {
      return this.tokens.isEmpty();
    }
    public int size() {
      return tokens.size() + tokens.values().stream()
        .filter(x -> !(x instanceof StopNode))
        .mapToInt(TNode::size)
        .sum();
    }

    TNode() {
    }
    TNode(String[] tokens) {
      add(tokens);
    }
  }
  public static class StopNode extends TNode {
    StopNode() {
    }
    @Override
    public int size() {
      return 1;
    }
    public boolean isTerminal() {
      return true;
    }
  }

  private final TNode root = new TNode();

  public void add(String[] tokens) {
    root.add(tokens);
  }

  public boolean contains(String token) {
    return root.contains(token);
  }

  public TNode get(String token) {
    return root.get(token);
  }

  public int size() {
    return root.size();
  }

}
