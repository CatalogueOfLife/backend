package life.catalogue.jobs;

import life.catalogue.common.id.IdConverter;
import life.catalogue.db.mapper.ArchivedNameUsageMapper.ArchivedSimpleName;
import life.catalogue.matching.NameIdentity;
import life.catalogue.matching.TaxGroupAnalyzer;
import life.catalogue.release.IdProvider;

import java.util.*;

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
 * ids of OTUs - are never redirected.
 *
 * OTUs, UNITE species hypotheses and BOLD BINs, had minted LATIN29 ids up to COL26.5 XR and use their code as the id
 * since (see IdProvider.otuId). A dead id whose name is such a code redirects to the live id of exactly that code, never
 * through NameIdentity, which has nothing to compare for two unranked names without authorship, and never to a LATIN29
 * id. A live OTU id is a redirect target for that rule only.
 */
public class RedirectPairing {
  private final NameIdentity identity = new NameIdentity();
  private final TaxGroupAnalyzer groupAnalyzer;
  private final IntSet liveKeys;
  private final IntSet liveBaseKeys;

  public enum Outcome {
    /** the dead id redirects to the live id */
    REDIRECT,
    /** the dead id of an OTU redirects to the live id of its code */
    OTU,
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
   * @param live the redirect target, or for any other outcome the best live id considered. For a contradicted dead id
   *             it is the live id that came closest, the one told apart by the attribute compared last
   *             ({@link NameIdentity.Conflict} order), so the verdict explains why there is no redirect.
   *             Null if there was none
   * @param verdict the evidence for the pairing with live, null without one
   */
  public record Pair(ArchivedSimpleName dead, @Nullable ArchivedSimpleName live, @Nullable NameIdentity.Verdict verdict,
                     Outcome outcome) {
    public boolean isRedirect() {
      return outcome == Outcome.REDIRECT || outcome == Outcome.OTU;
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

  // of contradicted pairings the one that got furthest through the comparison first, then an id a base release carries
  private static final Comparator<Scored> CLOSEST_FIRST = Comparator
    .comparing((Scored s) -> s.verdict.conflict, Comparator.nullsLast(Comparator.reverseOrder()))
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
    Map<String, ArchivedSimpleName> liveOtus = new HashMap<>();
    List<ArchivedSimpleName> dead = new ArrayList<>();
    for (var sn : group) {
      if (!isLatin29(sn.getId())) {
        if (carriedBy(sn, liveKeys) && sn.getId() != null && sn.getId().equals(IdProvider.otuId(sn, null))) {
          liveOtus.put(sn.getId(), sn);
        }
      } else if (carriedBy(sn, liveKeys)) {
        live.add(new Candidate(sn, facts(sn), carriedBy(sn, liveBaseKeys)));
      } else {
        dead.add(sn);
      }
    }
    List<Pair> pairs = new ArrayList<>(dead.size());
    for (var d : dead) {
      String otu = IdProvider.otuId(d, null);
      if (otu != null) {
        var target = liveOtus.get(otu);
        pairs.add(target == null ? new Pair(d, null, null, Outcome.NO_TARGET) : new Pair(d, target, null, Outcome.OTU));
      } else {
        pairs.add(pair(d, live));
      }
    }
    return pairs;
  }

  private Pair pair(ArchivedSimpleName dead, List<Candidate> live) {
    if (live.isEmpty()) {
      return new Pair(dead, null, null, Outcome.NO_TARGET);
    }
    final var facts = facts(dead);
    List<Scored> scored = new ArrayList<>(live.size());
    Scored closest = null;
    for (var c : live) {
      var v = identity.compare(facts, c.facts);
      if (!v.isContradicted()) {
        scored.add(new Scored(c, v));
      } else if (closest == null || CLOSEST_FIRST.compare(new Scored(c, v), closest) < 0) {
        closest = new Scored(c, v);
      }
    }
    if (scored.isEmpty()) {
      return new Pair(dead, closest.c.live, closest.verdict, Outcome.CONTRADICTED);
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
