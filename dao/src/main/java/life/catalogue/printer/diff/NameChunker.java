package life.catalogue.printer.diff;

import java.util.ArrayList;
import java.util.List;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

/**
 * Computes a character-level diff of two short name labels and returns ordered EQUAL/DELETE/INSERT
 * chunks. Uses java-diff-utils with includeEqualParts=true so equal runs are returned too.
 */
public class NameChunker {

  public static List<Chunk> chunks(String before, String after) {
    List<Character> a = toChars(before);
    List<Character> b = toChars(after);
    Patch<Character> patch = DiffUtils.diff(a, b, true); // includeEqualParts
    List<Chunk> out = new ArrayList<>();
    for (AbstractDelta<Character> d : patch.getDeltas()) {
      switch (d.getType()) {
        case EQUAL -> append(out, ChunkOp.EQUAL, d.getSource().getLines());
        case DELETE -> append(out, ChunkOp.DELETE, d.getSource().getLines());
        case INSERT -> append(out, ChunkOp.INSERT, d.getTarget().getLines());
        case CHANGE -> {
          append(out, ChunkOp.DELETE, d.getSource().getLines());
          append(out, ChunkOp.INSERT, d.getTarget().getLines());
        }
      }
    }
    return merge(out);
  }

  private static List<Character> toChars(String s) {
    List<Character> list = new ArrayList<>(s.length());
    for (int i = 0; i < s.length(); i++) {
      list.add(s.charAt(i));
    }
    return list;
  }

  private static void append(List<Chunk> out, ChunkOp op, List<Character> chars) {
    if (chars.isEmpty()) return;
    StringBuilder sb = new StringBuilder(chars.size());
    for (Character c : chars) sb.append(c.charValue());
    out.add(new Chunk(op, sb.toString()));
  }

  private static List<Chunk> merge(List<Chunk> in) {
    List<Chunk> out = new ArrayList<>(in.size());
    for (Chunk c : in) {
      if (!out.isEmpty() && out.get(out.size() - 1).op() == c.op()) {
        Chunk prev = out.remove(out.size() - 1);
        out.add(new Chunk(c.op(), prev.text() + c.text()));
      } else {
        out.add(c);
      }
    }
    return out;
  }
}
