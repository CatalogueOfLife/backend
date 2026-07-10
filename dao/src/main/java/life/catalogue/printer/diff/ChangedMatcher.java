package life.catalogue.printer.diff;

import life.catalogue.matching.similarity.StringSimilarity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pass 2 of the diff: pairs "removed" and "added" candidates that are similar enough to be a single
 * modification rather than an unrelated delete + insert. Candidates are blocked by their first token
 * (genus) so matching is near-linear instead of full quadratic. Identical pairs (similarity 100) are
 * "healed": dropped from all outputs, since an identical string on both sides is not a change (this
 * repairs rare local sort-order glitches from the attempts path).
 */
public class ChangedMatcher {

  public record Result(List<ChangedName> changed, List<String> removed, List<String> added) {}

  public static Result match(List<String> removed, List<String> added, double threshold, StringSimilarity similarity) {
    Map<String, List<Integer>> byBlock = new LinkedHashMap<>();
    for (int i = 0; i < added.size(); i++) {
      byBlock.computeIfAbsent(block(added.get(i)), k -> new ArrayList<>()).add(i);
    }
    boolean[] usedAdded = new boolean[added.size()];

    List<ChangedName> changed = new ArrayList<>();
    List<String> removedLeft = new ArrayList<>();

    for (String r : removed) {
      List<Integer> candidates = byBlock.getOrDefault(block(r), List.of());
      int bestIdx = -1;
      double bestSim = -1;
      boolean bestIdentical = false;
      for (int idx : candidates) {
        if (usedAdded[idx]) continue;
        String a = added.get(idx);
        if (r.equals(a)) { bestIdx = idx; bestIdentical = true; break; }
        double s = similarity.getSimilarity(r, a);
        if (s > bestSim) { bestSim = s; bestIdx = idx; }
      }
      if (bestIdx >= 0 && bestIdentical) {
        usedAdded[bestIdx] = true;           // healed: drop both, not a change
      } else if (bestIdx >= 0 && bestSim >= threshold) {
        usedAdded[bestIdx] = true;
        String a = added.get(bestIdx);
        changed.add(new ChangedName(r, a, NameChunker.chunks(r, a), bestSim));
      } else {
        removedLeft.add(r);
      }
    }

    List<String> addedLeft = new ArrayList<>();
    for (int i = 0; i < added.size(); i++) {
      if (!usedAdded[i]) addedLeft.add(added.get(i));
    }
    return new Result(changed, removedLeft, addedLeft);
  }

  /** Blocking key: the first whitespace-delimited token (genus), or the whole string if no space. */
  static String block(String name) {
    int sp = name.indexOf(' ');
    return sp < 0 ? name : name.substring(0, sp);
  }
}
