package life.catalogue.printer.diff;

import life.catalogue.printer.NamesDiff;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Patch;

/**
 * Whole-list Myers (LCS) diff via java-diff-utils. Reads both sides fully into memory, so it is
 * guarded by maxSize and intended for tests, cross-checking StreamingMergeDiffEngine, and small
 * inputs — never for the 10M-name scale. Reuses the shared pass-2 assembly so its classification
 * matches the streaming engine.
 */
public class MyersDiffEngine implements NamesDiffEngine {
  public static final int DEFAULT_MAX_SIZE = 200_000;
  private final int maxSize;

  public MyersDiffEngine() { this(DEFAULT_MAX_SIZE); }

  public MyersDiffEngine(int maxSize) { this.maxSize = maxSize; }

  @Override
  public NamesDiff diff(DiffInput a, DiffInput b, DiffOptions opts) {
    List<String> la = collect(a, "side 1");
    List<String> lb = collect(b, "side 2");

    Patch<String> patch = DiffUtils.diff(la, lb, false);
    List<String> removed = new ArrayList<>();
    List<String> added = new ArrayList<>();
    for (AbstractDelta<String> d : patch.getDeltas()) {
      switch (d.getType()) {
        case DELETE -> removed.addAll(d.getSource().getLines());
        case INSERT -> added.addAll(d.getTarget().getLines());
        case CHANGE -> {
          removed.addAll(d.getSource().getLines());
          added.addAll(d.getTarget().getLines());
        }
        case EQUAL -> { /* unchanged */ }
      }
    }
    return NamesDiffEngine.assemble(a.label(), b.label(), removed, added, opts);
  }

  private List<String> collect(DiffInput in, String which) {
    try (Stream<String> s = in.lines().get()) {
      List<String> list = new ArrayList<>();
      var it = s.iterator();
      while (it.hasNext()) {
        list.add(it.next());
        if (list.size() > maxSize) {
          throw new IllegalArgumentException("MyersDiffEngine input " + which + " (" + in.label()
            + ") exceeds max size " + maxSize + "; use StreamingMergeDiffEngine");
        }
      }
      return list;
    }
  }
}
