package life.catalogue.printer.diff;

import life.catalogue.matching.similarity.StringSimilarity;

import java.util.ArrayList;
import java.util.HashMap;
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
    // Pass A: global exact-match healing. Any string present in both sides (by multiplicity) is
    // unchanged and dropped from all outputs, independent of iteration order.
    Map<String, Integer> availableAdded = new HashMap<>();
    for (String a : added) {
      availableAdded.merge(a, 1, Integer::sum);
    }
    Map<String, Integer> healed = new HashMap<>();
    List<String> removedRemain = new ArrayList<>();
    for (String r : removed) {
      int avail = availableAdded.getOrDefault(r, 0);
      if (avail > 0) {
        availableAdded.put(r, avail - 1);
        healed.merge(r, 1, Integer::sum);
      } else {
        removedRemain.add(r);
      }
    }
    List<String> addedRemain = new ArrayList<>();
    for (String a : added) {
      int h = healed.getOrDefault(a, 0);
      if (h > 0) {
        healed.put(a, h - 1); // this occurrence was healed against a removed twin
      } else {
        addedRemain.add(a);
      }
    }

    // Pass B: fuzzy greedy pairing over the remainders. No identical pairs remain after Pass A.
    Map<String, List<Integer>> byBlock = new LinkedHashMap<>();
    for (int i = 0; i < addedRemain.size(); i++) {
      byBlock.computeIfAbsent(block(addedRemain.get(i)), k -> new ArrayList<>()).add(i);
    }
    boolean[] usedAdded = new boolean[addedRemain.size()];

    List<ChangedName> changed = new ArrayList<>();
    List<String> removedLeft = new ArrayList<>();

    for (String r : removedRemain) {
      List<Integer> candidates = byBlock.getOrDefault(block(r), List.of());
      int bestIdx = -1;
      double bestSim = -1;
      for (int idx : candidates) {
        if (usedAdded[idx]) continue;
        String a = addedRemain.get(idx);
        double s = similarity.getSimilarity(r, a);
        if (s > bestSim) { bestSim = s; bestIdx = idx; }
      }
      if (bestIdx >= 0 && bestSim >= threshold) {
        usedAdded[bestIdx] = true;
        String a = addedRemain.get(bestIdx);
        changed.add(new ChangedName(r, a, NameChunker.chunks(r, a), bestSim));
      } else {
        removedLeft.add(r);
      }
    }

    List<String> addedLeft = new ArrayList<>();
    for (int i = 0; i < addedRemain.size(); i++) {
      if (!usedAdded[i]) addedLeft.add(addedRemain.get(i));
    }
    return new Result(changed, removedLeft, addedLeft);
  }

  /** Blocking key: the first whitespace-delimited token (genus), or the whole string if no space. */
  static String block(String name) {
    int sp = name.indexOf(' ');
    return sp < 0 ? name : name.substring(0, sp);
  }
}
