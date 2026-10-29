package life.catalogue.basgroup;

import life.catalogue.api.model.Name;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.NomStatus;
import life.catalogue.common.tax.NameFormatter;
import life.catalogue.matching.similarity.ModifiedDamerauLevenshtein;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * Judges the name relations the data gives for a single pair of names: relations that do not fit the names,
 * contradict each other or merely repeat another one. The importer flags what it finds, extended releases also
 * remove the relations judged wrong or redundant, so the homotypic grouping does not build on them.
 * See https://github.com/CatalogueOfLife/data/issues/1815 and https://github.com/CatalogueOfLife/backend/issues/1662
 * <ol>
 *   <li>A spelling correction between names that differ in far more than their spelling is implausible.</li>
 *   <li>A later homonym is never homotypic with its senior homonym, so any homotypic relation besides it goes.</li>
 *   <li>A basionym next to a spelling correction, replacement name, superfluous or based on relation:
 *   the basionym stays only if it is plausible, see {@link #isPlausibleBasionym(Name, Name)}, the other relation goes.
 *   Otherwise the basionym goes, e.g. a misspelling exported as basionym of the correct name.</li>
 *   <li>A basionym next to a type relation: each is judged on its own and the implausible one goes,
 *   but only if the other is plausible.</li>
 *   <li>The same relation in both directions: the direction that fits the names stays,
 *   judged by the basionym authorship, their years or their nomenclatural status. If nothing tells, both stay.</li>
 *   <li>A homotypic relation next to a more specific one or given in both directions is redundant.</li>
 * </ol>
 * Exact duplicates are not judged here, callers collapse them.
 */
public class NameRelationRules {
  private static final int MIN_SPELLING_EDITS = 3;
  private static final ModifiedDamerauLevenshtein SPELLING_DISTANCE = new ModifiedDamerauLevenshtein();
  private static final Pattern YEAR = Pattern.compile("(\\d{4})");
  // the types that relate a basionym to the same name as the basionym itself would
  private static final Set<NomRelType> BASIONYM_RIVALS = Set.of(
    NomRelType.SPELLING_CORRECTION, NomRelType.REPLACEMENT_NAME, NomRelType.SUPERFLUOUS, NomRelType.BASED_ON
  );
  // directed types that point from the younger name to the older one
  private static final Set<NomRelType> CHRONOLOGICAL = Set.of(
    NomRelType.BASIONYM, NomRelType.REPLACEMENT_NAME, NomRelType.LATER_HOMONYM, NomRelType.SUPERFLUOUS, NomRelType.BASED_ON
  );

  private NameRelationRules() {
  }

  /**
   * A relation between two names given by their ids.
   */
  public record Rel(NomRelType type, String from, String to) {
    Rel reverse() {
      return new Rel(type, to, from);
    }
  }

  /**
   * @param rel the judged relation
   * @param issue what is wrong with it
   * @param remove true if the relation should go, false if it only deserves a flag as nothing tells which relation is right
   */
  public record Verdict(Rel rel, Issue issue, boolean remove) {
  }

  /**
   * Judges all relations between two names.
   *
   * @param n1 one name of the pair
   * @param n2 the other name of the pair, never the same as n1
   * @param relations the relations between both names, in any direction
   * @return the verdicts, a relation can be judged by more than one rule
   */
  public static List<Verdict> check(Name n1, Name n2, Collection<Rel> relations) {
    final Map<String, Name> names = Map.of(n1.getId(), n1, n2.getId(), n2);
    final Set<Rel> rels = new LinkedHashSet<>(relations);
    final List<Verdict> verdicts = new ArrayList<>();

    // 1. implausible spelling corrections
    if (!isPlausibleSpellingCorrection(n1, n2)) {
      remove(rels, verdicts, Issue.SPELLING_CORRECTION_IMPLAUSIBLE, r -> r.type() == NomRelType.SPELLING_CORRECTION);
    }

    // 2. later homonyms are heterotypic
    if (has(rels, NomRelType.LATER_HOMONYM)) {
      remove(rels, verdicts, Issue.NAME_RELATION_CONFLICT, r -> r.type().isHomotypic());
    }

    // 3. basionym or another homotypic type
    if (has(rels, NomRelType.BASIONYM) && rels.stream().anyMatch(r -> BASIONYM_RIVALS.contains(r.type()))) {
      boolean plausible = rels.stream()
        .anyMatch(r -> r.type() == NomRelType.BASIONYM && isPlausibleBasionym(names.get(r.from()), names.get(r.to())));
      if (plausible) {
        remove(rels, verdicts, Issue.NAME_RELATION_CONFLICT, r -> BASIONYM_RIVALS.contains(r.type()));
      } else {
        remove(rels, verdicts, Issue.NAME_RELATION_CONFLICT, r -> r.type() == NomRelType.BASIONYM);
      }
    }

    // 4. basionym or type
    if (has(rels, NomRelType.BASIONYM) && has(rels, NomRelType.TYPE)) {
      boolean basionym = rels.stream()
        .anyMatch(r -> r.type() == NomRelType.BASIONYM && isPlausibleBasionym(names.get(r.from()), names.get(r.to())));
      boolean type = rels.stream()
        .anyMatch(r -> r.type() == NomRelType.TYPE && isPlausibleType(names.get(r.from()), names.get(r.to())));
      if (basionym && !type) {
        remove(rels, verdicts, Issue.NAME_RELATION_CONFLICT, r -> r.type() == NomRelType.TYPE);
      } else if (type && !basionym) {
        remove(rels, verdicts, Issue.NAME_RELATION_CONFLICT, r -> r.type() == NomRelType.BASIONYM);
      } else {
        flag(rels, verdicts, r -> r.type() == NomRelType.BASIONYM || r.type() == NomRelType.TYPE);
      }
    }

    // 5. the same directed relation both ways
    for (var type : rels.stream().map(Rel::type).filter(t -> t != NomRelType.HOMOTYPIC).collect(Collectors.toSet())) {
      var forward = new Rel(type, n1.getId(), n2.getId());
      var backward = forward.reverse();
      if (rels.contains(forward) && rels.contains(backward)) {
        var keep = direction(type, n1, n2);
        if (keep == null) {
          flag(rels, verdicts, r -> r.equals(forward) || r.equals(backward));
        } else {
          var wrong = keep.equals(forward) ? backward : forward;
          remove(rels, verdicts, Issue.NAME_RELATION_CONFLICT, r -> r.equals(wrong));
        }
      }
    }

    // 6. redundant homotypic relations
    if (rels.stream().anyMatch(r -> r.type() != NomRelType.HOMOTYPIC && r.type().isHomotypic())) {
      remove(rels, verdicts, Issue.NAME_RELATION_REDUNDANT, r -> r.type() == NomRelType.HOMOTYPIC);
    } else {
      var homotypic = rels.stream().filter(r -> r.type() == NomRelType.HOMOTYPIC).toList();
      if (homotypic.size() > 1) {
        var first = homotypic.get(0);
        remove(rels, verdicts, Issue.NAME_RELATION_REDUNDANT, r -> r.type() == NomRelType.HOMOTYPIC && !r.equals(first));
      }
    }
    return verdicts;
  }

  private static boolean has(Set<Rel> rels, NomRelType type) {
    return rels.stream().anyMatch(r -> r.type() == type);
  }

  private static void remove(Set<Rel> rels, List<Verdict> verdicts, Issue issue, Predicate<Rel> filter) {
    var iter = rels.iterator();
    while (iter.hasNext()) {
      var r = iter.next();
      if (filter.test(r)) {
        verdicts.add(new Verdict(r, issue, true));
        iter.remove();
      }
    }
  }

  private static void flag(Set<Rel> rels, List<Verdict> verdicts, Predicate<Rel> filter) {
    for (var r : rels) {
      if (filter.test(r)) {
        verdicts.add(new Verdict(r, Issue.NAME_RELATION_CONFLICT, false));
      }
    }
  }

  /**
   * Decides which direction of a relation given both ways fits the names.
   * @return the relation to keep or null if nothing tells
   */
  @Nullable
  private static Rel direction(NomRelType type, Name n1, Name n2) {
    var forward = new Rel(type, n1.getId(), n2.getId());
    if (type == NomRelType.BASIONYM || type == NomRelType.TYPE) {
      boolean f = type == NomRelType.BASIONYM ? isPlausibleBasionym(n1, n2) : isPlausibleType(n1, n2);
      boolean b = type == NomRelType.BASIONYM ? isPlausibleBasionym(n2, n1) : isPlausibleType(n2, n1);
      if (f != b) {
        return f ? forward : forward.reverse();
      }
    }
    if (CHRONOLOGICAL.contains(type)) {
      Integer y1 = year(n1);
      Integer y2 = year(n2);
      if (y1 != null && y2 != null && !y1.equals(y2)) {
        return y1 > y2 ? forward : forward.reverse();
      }
    }
    // the related name of a replacement name, later homonym, based on, superfluous name or spelling correction
    // is the one not to be used
    if (type != NomRelType.BASIONYM && type != NomRelType.TYPE) {
      boolean u1 = unusable(n1);
      boolean u2 = unusable(n2);
      if (u1 != u2) {
        return u2 ? forward : forward.reverse();
      }
    }
    return null;
  }

  private static boolean unusable(Name n) {
    return n.getNomStatus() != null && (n.getNomStatus() == NomStatus.UNACCEPTABLE || n.getNomStatus() == NomStatus.REJECTED
      || !n.getNomStatus().isAvailable());
  }

  @Nullable
  static Integer year(Name n) {
    var y = year(n.getCombinationAuthorship());
    return y != null ? y : n.getPublishedInYear();
  }

  @Nullable
  private static Integer year(@Nullable Authorship a) {
    if (a != null && a.getYear() != null) {
      Matcher m = YEAR.matcher(a.getYear());
      if (m.find()) {
        return Integer.parseInt(m.group(1));
      }
    }
    return null;
  }

  /**
   * The groups of ranks a basionym may move within, see ICN Art. 6.10: a subspecies can be the basionym of a species,
   * a subgenus of a genus and a tribe of a family, but a species is never the basionym of a genus.
   */
  private enum RankGroup {SPECIES, GENUS, FAMILY, HIGHER}

  /**
   * @return the rank group or null if the rank is unknown and the name has no specific epithet either
   */
  @Nullable
  private static RankGroup rankGroup(Name n) {
    Rank r = n.getRank();
    if (r == null || r.otherOrUnranked()) {
      return n.getSpecificEpithet() != null ? RankGroup.SPECIES : null;
    }
    if (r.isSpeciesOrBelow()) return RankGroup.SPECIES;
    if (r.isGenusGroup()) return RankGroup.GENUS;
    if (r.isFamilyGroup()) return RankGroup.FAMILY;
    return RankGroup.HIGHER;
  }

  /**
   * A basionym relates names of the same rank group, their rank may differ, e.g. a subspecies raised to a species
   * or a subgenus to a genus. The name with the basionym cites the basionym authorship, i.e. the authors in brackets,
   * the basionym itself has none.
   *
   * @param name the name having the basionym
   * @param basionym its basionym
   */
  public static boolean isPlausibleBasionym(Name name, Name basionym) {
    var g1 = rankGroup(name);
    var g2 = rankGroup(basionym);
    return (g1 == null || g2 == null || g1 == g2)
      && hasBasionymAuthorship(name) && !hasBasionymAuthorship(basionym);
  }

  private static boolean hasBasionymAuthorship(Name n) {
    return n.getBasionymAuthorship() != null && n.getBasionymAuthorship().exists();
  }

  /**
   * A type relation goes from a name to the name of the next lower rank group typifying it,
   * e.g. a genus typified by a species or a family by a genus.
   * Names with an unknown rank are given the benefit of the doubt.
   *
   * @param name the higher ranked name
   * @param type the name typifying it
   */
  public static boolean isPlausibleType(Name name, Name type) {
    var g1 = rankGroup(name);
    var g2 = rankGroup(type);
    return g1 == null || g2 == null || g1.ordinal() > g2.ordinal();
  }

  /**
   * @return true if the canonical names of both names are so close that one can be a spelling of the other:
   *   they must not differ in more than a fifth of their characters, with at least 3 edits allowed.
   */
  public static boolean isPlausibleSpellingCorrection(Name n1, Name n2) {
    String c1 = NameFormatter.canonicalName(n1);
    String c2 = NameFormatter.canonicalName(n2);
    if (c1 == null || c2 == null) {
      return true;
    }
    c1 = c1.toLowerCase();
    c2 = c2.toLowerCase();
    int maxEdits = Math.max(MIN_SPELLING_EDITS, Math.max(c1.length(), c2.length()) / 5);
    return SPELLING_DISTANCE.getEditDistance(c1, c2) <= maxEdits;
  }
}
