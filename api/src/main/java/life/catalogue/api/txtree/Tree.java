package life.catalogue.api.txtree;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple class to keep a taxonomy of names as can be expressed with the text-tree format:
 * https://github.com/gbif/text-tree
 * It is a very simple text based tree format that is very easy to read.
 * Especially useful for larger tree snippets.
 */
public class Tree implements Iterable<TreeNode> {
  public static final String SYNONYM_SYMBOL = "*";
  public static final String BASIONYM_SYMBOL = "$";
  private static final Logger LOG = LoggerFactory.getLogger(Tree.class);

  private long count;
  private TreeNode root = new TreeNode(0, null, null, false);
  private static final Pattern LINE_PARSER = Pattern.compile("^" +
      "( *)" +  // indent #1
      "(\\" + SYNONYM_SYMBOL + ")?" +  // #2
      "(\\" + BASIONYM_SYMBOL + ")?" +  // #3
      "(.+?)" +   // name & author #4
      "(?: \\[([a-z]+)])?" +  // rank #5
      "(?: +#.*)?" +  // comments
      " *$");


  public long getCount() {
    return count;
  }

  public static boolean verify(InputStream stream) throws IOException {
    BufferedReader br = new BufferedReader(new InputStreamReader(stream));
    String line = br.readLine();
    int counter = 0;
    try {
      while (line != null) {
        int level = 0;
        if (!StringUtils.isBlank(line)) {
          Matcher m = LINE_PARSER.matcher(line);
          if (m.find()) {
            parseRank(m); // make sure we can read all ranks
            level = m.group(1).length();
            if (level % 2 != 0) {
              LOG.error("Tree is not indented properly on line {}. Use 2 spaces only: {}", counter, line);
              return false;
            }
          } else {
            LOG.error("Failed to parse Tree on line {}: {}", counter, line);
            return false;
          }
        }
        line = br.readLine();
        counter++;
      }
    } catch (IllegalArgumentException e) {
      LOG.error("Failed to parse Tree on line {}: {}", counter, line, e);
      return false;
    }
    return true;
  }

  public static Tree read(InputStream stream) throws IOException {
    return read(stream, null);
  }

  public static Tree read(InputStream stream, @Nullable Consumer<TreeLine> listener) throws IOException {
    Tree tree = new Tree();
    LinkedList<TreeNode> parents = Lists.newLinkedList();
    
    BufferedReader br = new BufferedReader(new InputStreamReader(stream));
    long row = 1;
    String line = br.readLine();
    while (line != null) {
      int level = 0;
      if (!StringUtils.isBlank(line)) {
        tree.count++;
        Matcher m = LINE_PARSER.matcher(line);
        if (m.find()) {
          level = m.group(1).length();
          if (level % 2 != 0) {
            throw new IllegalArgumentException("Tree is not indented properly on line " + row + ". Use 2 spaces only: " + line);
          }
          level = level / 2;
          
          TreeNode n = node(row, m);
          if (level == 0) {
            tree.getRoot().children.add(n);
            parents.clear();
            parents.add(n);
            
          } else {
            while (parents.size() > level) {
              // remove latest parents until we are at the right level
              parents.removeLast();
            }
            if (parents.size() < level) {
              throw new IllegalArgumentException("Tree is not properly indented on line " + row + ". Use 2 spaces for children: " + line);
            }
            TreeNode p = parents.peekLast();
            if (m.group(2) != null) {
              p.synonyms.add(n);
            } else {
              p.children.add(n);
            }
            parents.add(n);
          }

          if (listener != null) {
            TreeLine tl = new TreeLine(row, level, line.trim());
            listener.accept(tl);
          }
        } else {
          throw new IllegalArgumentException("Failed to parse Tree on line " + row + ": " + line);
        }
      }
      line = br.readLine();
      row++;
    }
    return tree;
  }
  
  private static TreeNode node(long row, Matcher m) {
    boolean basionym = m.group(3) != null;
    String name = m.group(4).trim();
    Rank rank = parseRank(m);
    return new TreeNode(row, name, rank, basionym);
  }

  private static Rank parseRank(Matcher m) throws IllegalArgumentException {
    if (m.group(5) != null) {
      return Rank.valueOf(m.group(5).toUpperCase());
    }
    return Rank.UNRANKED;
  }

  public TreeNode getRoot() {
    return root;
  }
  
  public void print(Appendable out) throws IOException {
    for (TreeNode n : root.children) {
      n.print(out, 0, false);
    }
  }
  
  @Override
  public Iterator<TreeNode> iterator() {
    return new NNIterator(this);
  }
  
  private class NNIter {
    private int synIdx;
    private final TreeNode node;
    
    NNIter(TreeNode node) {
      this.node = node;
    }
    
    public boolean moreSynonyms() {
      return node.synonyms.size() > synIdx;
    }
    
    public NNIter nextSynonym() {
      TreeNode n = node.synonyms.get(synIdx);
      synIdx++;
      return new NNIter(n);
    }
  }
  
  private class NNIterator implements Iterator<TreeNode> {
    private LinkedList<NNIter> stack = Lists.newLinkedList();
    private NNIter curr = null;
    
    NNIterator(Tree tree) {
      for (TreeNode r : tree.getRoot().children) {
        this.stack.addFirst(new NNIter(r));
      }
    }
    
    @Override
    public boolean hasNext() {
      return !stack.isEmpty() || (curr != null && curr.moreSynonyms());
    }
    
    @Override
    public TreeNode next() {
      if (curr == null) {
        poll();
        return curr.node;
        
      } else if (curr.moreSynonyms()) {
        return curr.nextSynonym().node;
        
      } else {
        poll();
        return curr.node;
      }
    }
    
    private void poll() {
      curr = stack.removeLast();
      while (!curr.node.children.isEmpty()) {
        stack.add(new NNIter(curr.node.children.removeLast()));
      }
    }
    
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
