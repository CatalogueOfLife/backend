package org.col.admin.task.importer.txttree;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.col.admin.task.importer.neo.printer.TxtPrinter;
import org.gbif.nameparser.api.Rank;
import org.gbif.utils.file.FileUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple class to keep a taxonomy of names.
 * We use this to compare nub build outputs with a very simple text based tree format that is very easy to read.
 * Especially useful for larger tree snippets.
 */
public class Tree implements Iterable<TreeNode> {
  private long count;
  private TreeNode root = new TreeNode(null, null, false);
  private static final Pattern LINE_PARSER = Pattern.compile("^" +
      "( *)" +  // indent #1
      "(\\" + TxtPrinter.SYNONYM_SYMBOL + ")?" +  // #2
      "(\\" + TxtPrinter.BASIONYM_SYMBOL + ")?" +  // #3
      "(.+?)" +   // name & author #4
      "(?: \\[([a-z]+)])?" +  // rank #5
      " *$");

  public static Tree read(String classpathFilename) throws IOException {
    return read(FileUtils.classpathStream(classpathFilename));
  }

  public long getCount() {
    return count;
  }

  public static Tree read(InputStream stream) throws IOException {
    Tree tree = new Tree();
    LinkedList<TreeNode> parents = Lists.newLinkedList();

    BufferedReader br = new BufferedReader(new InputStreamReader(stream));
    int counter = 1;
    String line = br.readLine();
    while (line != null) {
      int level = 0;
      if (!StringUtils.isBlank(line)) {
        tree.count++;
        Matcher m = LINE_PARSER.matcher(line);
        if (m.find()) {
          level = m.group(1).length();
          if (level % 2 != 0) {
            throw new IllegalArgumentException("Tree is not indented properly on line "+counter+". Use 2 spaces only: " + line);
          }
          level = level / 2;

          if (level == 0) {
            TreeNode n = node(m);
            tree.getRoot().children.add(n);
            parents.clear();
            parents.add(n);

          } else {
            TreeNode n = node(m);
            while (parents.size() > level) {
              // remove latest parents until we are at the right level
              parents.removeLast();
            }
            if (parents.size() < level) {
              throw new IllegalArgumentException("Tree is not properly indented on line "+counter+". Use 2 spaces for children: " + line);
            }
            TreeNode p = parents.peekLast();
            if (m.group(2) != null) {
              p.synonyms.add(n);
            } else {
              p.children.add(n);
            }
            parents.add(n);
          }
        } else {
          throw new IllegalArgumentException("Failed to parse Tree on line "+counter+": " + line);
        }
      }
      line = br.readLine();
      counter++;
    }
    return tree;
  }

  private static TreeNode node(Matcher m) {
    final boolean basionym = m.group(3) != null;
    final String name = m.group(4).trim();
    Rank rank = Rank.UNRANKED;
    if (m.group(5) != null) {
      rank = Rank.valueOf(m.group(5).toUpperCase());
    }
    return new TreeNode(name, rank, basionym);
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
