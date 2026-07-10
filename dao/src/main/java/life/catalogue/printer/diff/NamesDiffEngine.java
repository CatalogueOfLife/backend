package life.catalogue.printer.diff;

import life.catalogue.matching.similarity.NormalizedLevenshtein;
import life.catalogue.printer.NamesDiff;

import java.util.List;

public interface NamesDiffEngine {

  NamesDiff diff(DiffInput a, DiffInput b, DiffOptions opts);

  /**
   * Shared pass-2 + truncation used by all engines. Runs ChangedMatcher over the raw removed/added
   * candidate lists, then caps each output list at opts.maxItems (0 = unlimited), flagging truncation.
   */
  static NamesDiff assemble(String label1, String label2, List<String> removed, List<String> added, DiffOptions opts) {
    ChangedMatcher.Result m = ChangedMatcher.match(removed, added, opts.getChangedThreshold(), new NormalizedLevenshtein());
    NamesDiff diff = new NamesDiff(label1, label2);
    boolean truncated = false;
    truncated |= addCapped(diff.getRemoved(), m.removed(), opts.getMaxItems());
    truncated |= addCapped(diff.getAdded(), m.added(), opts.getMaxItems());
    truncated |= addCapped(diff.getChanged(), m.changed(), opts.getMaxItems());
    diff.setTruncated(truncated);
    return diff;
  }

  private static <T> boolean addCapped(List<T> target, List<T> src, int maxItems) {
    if (maxItems <= 0 || src.size() <= maxItems) {
      target.addAll(src);
      return false;
    }
    target.addAll(src.subList(0, maxItems));
    return true;
  }
}
