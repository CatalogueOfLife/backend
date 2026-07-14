package life.catalogue.printer.diff;

import life.catalogue.api.model.ScientificName;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.similarity.LevenshteinDistance;
import life.catalogue.matching.similarity.NormalizedLevenshtein;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.UnparsableNameException;
import org.gbif.nameparser.util.NameFormatter;

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
  // reuse the shared parser instance (the GBIF NameParser API, now backed by the Rust implementation),
  // avoiding the overhead of building a full CoL Name and a second parser + executor.
  private static final org.gbif.nameparser.api.NameParser PARSER = NameParser.PARSER.gbif();
  public record Result(List<ChangedName> changed, List<String> removed, List<String> added) {}

  /** A parsed candidate: raw label, its normalised canonical (authorship stripped) and parsed authorship. */
  private static final class Candidate {
    final String label;
    final String normCanonical;
    final ScientificName sciname;

    Candidate(String label, String normCanonical, ScientificName sciname) {
      this.label = label;
      this.normCanonical = normCanonical;
      this.sciname = sciname;
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
   * When exactly one candidate is eligible it is paired unconditionally (1-in-1-out). When several
   * same-canonical variants compete, only a non-{@link Equality#DIFFERENT} author/year match is paired,
   * preferring the closest canonical and, among ties, an {@link Equality#EQUAL} author over an
   * {@link Equality#UNKNOWN} one.
   */
  private static int choose(Candidate r, List<Candidate> add, List<Integer> eligible, int canonicalMaxDistance) {
    if (eligible.isEmpty()) return -1;
    if (eligible.size() == 1) return eligible.get(0); // 1-in-1-out: pair regardless of author (e.g. Mill.->L.)
    // Several same-canonical variants compete: pair only a non-DIFFERENT author/year, best first.
    int best = -1;
    int bestScore = Integer.MIN_VALUE;
    for (int idx : eligible) {
      Candidate a = add.get(idx);
      Equality eq = authorEquality(r.sciname, a.sciname);
      if (eq == Equality.DIFFERENT) continue;
      int d = LevenshteinDistance.getDistance(r.normCanonical, a.normCanonical);
      int score = (canonicalMaxDistance - d) * 10 + (eq == Equality.EQUAL ? 2 : 1);
      if (score > bestScore) {
        bestScore = score;
        best = idx;
      }
    }
    return best;
  }

  /**
   * Author/year comparison of the effective authorship, treating a missing authorship on either side as
   * UNKNOWN (poolable, not DIFFERENT). We compare the effective {@link Authorship} rather than the whole
   * {@link ScientificName}, because AuthorComparator's ScientificName overload can only promote a
   * cross basionym/combination match to EQUAL or UNKNOWN, never DIFFERENT — so year-differing parenthetical
   * synonyms (author in the basionym slot on one side, combination slot on the other) would wrongly pool.
   */
  private static Equality authorEquality(ScientificName a1, ScientificName a2) {
    Authorship e1 = effectiveAuthorship(a1);
    Authorship e2 = effectiveAuthorship(a2);
    if (e1 == null || e2 == null || e1.isEmpty() || e2.isEmpty()) {
      return Equality.UNKNOWN;
    }
    return AUTHOR_CMP.compare(e1, e2);
  }

  /** The authorship carrying the author+year: the combination authorship, or the basionym for parenthetical names. */
  private static Authorship effectiveAuthorship(ScientificName n) {
    if (n == null) return null;
    Authorship comb = n.getCombinationAuthorship();
    return (comb != null && !comb.isEmpty()) ? comb : n.getBasionymAuthorship();
  }

  /** Parses a label into its normalised canonical and authorship; falls back to the raw label if unparsable. */
  private static Candidate parse(String label) {
    try {
      // name-parser 5.0: parse() never throws — it returns a sealed ParseResult; orElseThrow() yields the
      // ParsedName or throws UnparsableNameException for the unparsable fallback below.
      var pn = PARSER.parse(label).orElseThrow();
      String canonical = NameFormatter.canonicalWithoutAuthorship(pn);
      ScientificName sciname = ScientificName.wrap(pn);
      if (canonical == null || canonical.isBlank()) {
        canonical = label;
      }
      return new Candidate(label, SciNameNormalizer.normalize(canonical), sciname);

    } catch (UnparsableNameException e) {
      return new Candidate(label, SciNameNormalizer.normalize(label), null);
    }
  }

  /** Blocking key: the first whitespace-delimited token (genus), or the whole string if no space. */
  static String block(String name) {
    int sp = name.indexOf(' ');
    return sp < 0 ? name : name.substring(0, sp);
  }
}
