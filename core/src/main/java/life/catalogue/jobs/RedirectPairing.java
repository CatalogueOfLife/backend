package life.catalogue.jobs;

import life.catalogue.common.id.IdConverter;
import life.catalogue.db.mapper.ArchivedNameUsageMapper.ArchivedSimpleName;
import life.catalogue.matching.NameIdentity;
import life.catalogue.matching.TaxGroupAnalyzer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * Works out, for one canonical group of the name usage archive, which live id each dead id should redirect to.
 *
 * Live ids are the ones a live release carries, see ReleaseRanking.liveKeys; all other ids of the group are dead.
 * A dead id is paired with every live id through {@link NameIdentity}, the same comparison the id provider uses to
 * reuse an identifier, on the archived versions of both. A contradicted pairing is never a redirect, and like a
 * resurrection a redirect needs positive agreement on authorship or rank: evidence PLAUSIBLE or better.
 * Of what is left the best verdict wins, a tie goes to the id a base release carries over one only extended
 * releases carry, and anything still tied is ambiguous and gets no redirect.
 *
 * Unlike the id provider this is not a one to one hand out: many dead ids can redirect to the same live id, typically
 * several duplicates that were removed over time. Ids that are no LATIN29 integers - temporary ids and the verbatim
 * ids of OTUs - are neither redirected nor redirect targets.
 */
public class RedirectPairing {
  private final NameIdentity identity = new NameIdentity();
  private final TaxGroupAnalyzer groupAnalyzer;
  private final IntSet liveKeys;
  private final IntSet liveBaseKeys;

  public enum Outcome {
    /** the dead id redirects to the live id */
    REDIRECT,
    /** two live ids are equally good, no redirect */
    AMBIGUOUS,
    /** nothing contradicts the best live id, but nothing corroborates it either, no redirect */
    WEAK,
    /** every live id is a different name, no redirect */
    CONTRADICTED,
    /** the group has no live id at all */
    NO_TARGET
  }

  /**
   * The decision for one dead id.
   * @param live the redirect target, or for any other outcome the best live id considered. Null if there was none
   * @param verdict the evidence for the pairing with live, null without one
   */
  public record Pair(ArchivedSimpleName dead, @Nullable ArchivedSimpleName live, @Nullable NameIdentity.Verdict verdict,
                     Outcome outcome) {
    public boolean isRedirect() {
      return outcome == Outcome.REDIRECT;
    }
  }

  private record Candidate(ArchivedSimpleName live, NameIdentity.Facts facts, boolean base) {
  }

  private record Scored(Candidate c, NameIdentity.Verdict verdict) {
  }

  // best verdict first, then an id a base release carries
  private static final Comparator<Scored> BEST_FIRST = Comparator
    .comparing(Scored::verdict, Comparator.reverseOrder())
    .thenComparing(s -> !s.c.base);

  /**
   * @param liveKeys the keys of the releases carrying live ids
   * @param liveBaseKeys the keys among them that are base releases
   */
  public RedirectPairing(TaxGroupAnalyzer groupAnalyzer, IntSet liveKeys, IntSet liveBaseKeys) {
    this.groupAnalyzer = groupAnalyzer;
    this.liveKeys = liveKeys;
    this.liveBaseKeys = liveBaseKeys;
  }

  /**
   * @param group all archived usages of one canonical names index id that should be considered, live and dead
   * @return one pair for every dead id of the group with a LATIN29 id, in the order given
   */
  public List<Pair> pair(List<ArchivedSimpleName> group) {
    List<Candidate> live = new ArrayList<>();
    List<ArchivedSimpleName> dead = new ArrayList<>();
    for (var sn : group) {
      if (!isLatin29(sn.getId())) {
        continue;
      }
      if (carriedBy(sn, liveKeys)) {
        live.add(new Candidate(sn, facts(sn), carriedBy(sn, liveBaseKeys)));
      } else {
        dead.add(sn);
      }
    }
    List<Pair> pairs = new ArrayList<>(dead.size());
    for (var d : dead) {
      pairs.add(pair(d, live));
    }
    return pairs;
  }

  private Pair pair(ArchivedSimpleName dead, List<Candidate> live) {
    if (live.isEmpty()) {
      return new Pair(dead, null, null, Outcome.NO_TARGET);
    }
    final var facts = facts(dead);
    List<Scored> scored = new ArrayList<>(live.size());
    for (var c : live) {
      var v = identity.compare(facts, c.facts);
      if (!v.isContradicted()) {
        scored.add(new Scored(c, v));
      }
    }
    if (scored.isEmpty()) {
      return new Pair(dead, null, null, Outcome.CONTRADICTED);
    }
    scored.sort(BEST_FIRST);
    var best = scored.get(0);
    if (best.verdict.evidence.compareTo(NameIdentity.Evidence.PLAUSIBLE) < 0) {
      return new Pair(dead, best.c.live, best.verdict, Outcome.WEAK);
    }
    if (scored.size() > 1 && BEST_FIRST.compare(best, scored.get(1)) == 0) {
      return new Pair(dead, best.c.live, best.verdict, Outcome.AMBIGUOUS);
    }
    return new Pair(dead, best.c.live, best.verdict, Outcome.REDIRECT);
  }

  /**
   * The facts of an archived usage, built exactly as the id provider builds them for a released id.
   */
  private NameIdentity.Facts facts(ArchivedSimpleName sn) {
    sn.setGroup(groupAnalyzer.analyze(sn, sn.getClassification()));
    return new NameIdentity.Facts(sn.getRank(), sn.getAuthorship(), sn.getPhrase(), sn.getStatus(), sn.getCode(),
      sn.getGroup(), sn.getParent());
  }

  static boolean carriedBy(ArchivedSimpleName sn, IntSet keys) {
    if (sn.getReleaseKeys() != null) {
      for (int k : sn.getReleaseKeys()) {
        if (keys.contains(k)) {
          return true;
        }
      }
    }
    return false;
  }

  static boolean isLatin29(String id) {
    try {
      IdConverter.LATIN29.decode(id);
      return true;
    } catch (IllegalArgumentException | NullPointerException e) {
      return false;
    }
  }
}
