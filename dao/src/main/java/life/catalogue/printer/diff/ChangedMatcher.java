package life.catalogue.printer.diff;

import life.catalogue.api.model.Name;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.similarity.LevenshteinDistance;
import life.catalogue.matching.similarity.NormalizedLevenshtein;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Authorship;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pass 2 of the diff: pairs "removed" and "added" candidates that are the same name with only its
 * authorship (or a single-character canonical typo) changed, rather than an unrelated delete + insert.
 *
 * <p>Candidates are blocked by their first token (genus). Within a block a removed is paired with an
 * added when their authorship-stripped, {@link SciNameNormalizer#normalize(String) normalised} canonicals
 * are within {@code canonicalMaxDistance} edit operations. When exactly one added candidate is eligible it
 * is paired unconditionally (covers added/removed/replaced authorship, e.g. {@code Mill.}→{@code L.}); when
 * several same-canonical variants compete, {@link AuthorComparator} (year-aware) is used as a tie-break so
 * distinct synonyms are not mixed up.
 *
 * <p>Pass A first heals identical strings (present on both sides), independent of iteration order; those are
 * not changes. Only the diff remainder reaches the parse-based pass B, so per-candidate parsing is cheap.
 */
public class ChangedMatcher {

  private static final AuthorComparator AUTHOR_CMP = new AuthorComparator(AuthorshipNormalizer.INSTANCE);
  private static final NormalizedLevenshtein DISPLAY_SIM = new NormalizedLevenshtein();

  public record Result(List<ChangedName> changed, List<String> removed, List<String> added) {}

  /** A parsed candidate: raw label, its normalised canonical (authorship stripped) and parsed authorship. */
  private static final class Candidate {
    final String label;
    final String normCanonical;
    final Authorship authorship; // null when the label could not be parsed

    Candidate(String label, String normCanonical, Authorship authorship) {
      this.label = label;
      this.normCanonical = normCanonical;
      this.authorship = authorship;
    }
  }

  public static Result match(List<String> removed, List<String> added, int canonicalMaxDistance) {
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

    // Pass B: canonical-based pairing over the remainders. No identical pairs remain after Pass A.
    List<Candidate> rem = new ArrayList<>(removedRemain.size());
    for (String s : removedRemain) rem.add(parse(s));
    List<Candidate> add = new ArrayList<>(addedRemain.size());
    for (String s : addedRemain) add.add(parse(s));

    Map<String, List<Integer>> byBlock = new LinkedHashMap<>();
    for (int i = 0; i < add.size(); i++) {
      byBlock.computeIfAbsent(block(add.get(i).label), k -> new ArrayList<>()).add(i);
    }
    boolean[] usedAdded = new boolean[add.size()];

    List<ChangedName> changed = new ArrayList<>();
    List<String> removedLeft = new ArrayList<>();

    for (Candidate r : rem) {
      List<Integer> candidates = byBlock.getOrDefault(block(r.label), List.of());
      List<Integer> eligible = new ArrayList<>();
      for (int idx : candidates) {
        if (usedAdded[idx]) continue;
        int d = LevenshteinDistance.getDistance(r.normCanonical, add.get(idx).normCanonical);
        if (d <= canonicalMaxDistance) eligible.add(idx);
      }
      int chosen = choose(r, add, eligible, canonicalMaxDistance);
      if (chosen >= 0) {
        usedAdded[chosen] = true;
        Candidate a = add.get(chosen);
        changed.add(new ChangedName(r.label, a.label, NameChunker.chunks(r.label, a.label),
          DISPLAY_SIM.getSimilarity(r.label, a.label)));
      } else {
        removedLeft.add(r.label);
      }
    }

    List<String> addedLeft = new ArrayList<>();
    for (int i = 0; i < add.size(); i++) {
      if (!usedAdded[i]) addedLeft.add(add.get(i).label);
    }
    return new Result(changed, removedLeft, addedLeft);
  }

  /**
   * Picks the added candidate index to pair with the removed one, or -1 for none.
   * Task 2: pair only when exactly one candidate is eligible. Extended in Task 3.
   */
  private static int choose(Candidate r, List<Candidate> add, List<Integer> eligible, int canonicalMaxDistance) {
    return eligible.size() == 1 ? eligible.get(0) : -1;
  }

  /** Parses a label into its normalised canonical and authorship; falls back to the raw label if unparsable. */
  private static Candidate parse(String label) {
    var opt = NameParser.PARSER.parse(label);
    if (opt.isPresent()) {
      Name n = opt.get().getName();
      String canonical = n.getScientificName();
      if (canonical == null || canonical.isBlank()) {
        canonical = label;
      }
      return new Candidate(label, SciNameNormalizer.normalize(canonical), n.getCombinationAuthorship());
    }
    return new Candidate(label, SciNameNormalizer.normalize(label), null);
  }

  /** Blocking key: the first whitespace-delimited token (genus), or the whole string if no space. */
  static String block(String name) {
    int sp = name.indexOf(' ');
    return sp < 0 ? name : name.substring(0, sp);
  }
}
