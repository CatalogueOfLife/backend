package life.catalogue.api.txtree;

import life.catalogue.common.func.ThrowingConsumer;

import life.catalogue.common.lang.InterruptedRuntimeException;

import org.gbif.nameparser.api.Rank;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

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
      "([^\t;]+?)" +   // name & author #4
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
      int max = 0;
      int last = 0;
      while (line != null) {
        if (!StringUtils.isBlank(line)) {
          Matcher m = LINE_PARSER.matcher(line);
          if (m.find()) {
            parseRank(m); // make sure we can read all ranks
            int level = m.group(1).length();
            max = Math.max(max, level);
            if (level % 2 != 0) {
              LOG.error("Tree is not indented properly on line {}. Use 2 spaces only: {}", counter, line);
              return false;
            }
            if (level-last>2) {
              LOG.error("Tree is indented too much on line {}. Use 2 spaces only: {}", counter, line);
              return false;
            }
            last = level;
          } else {
            LOG.error("Failed to parse Tree on line {}: {}", counter, line);
            return false;
          }
        }
        line = br.readLine();
        counter++;
      }
      if (max==0 && counter > 10) {
        LOG.error("Tree is not indented at all");
        return false;
      }

    } catch (IllegalArgumentException e) {
      LOG.error("Failed to parse Tree on line {}: {}", counter, line, e);
      return false;
    }
    // should we require some other level than just 0???
    return true;
  }

  public static Tree read(InputStream stream) throws IOException {
    try {
      return read(stream, null);
    } catch (InterruptedException e) {
      // we dont have any listener that would throw this, never happens
      throw new InterruptedRuntimeException(e.getMessage());
    }
  }

  public static Tree read(InputStream stream, @Nullable ThrowingConsumer<TreeLine, InterruptedException> listener) throws IOException, InterruptedException {
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
            listener.acceptThrows(tl);
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
