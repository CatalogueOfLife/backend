package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.collection.CollectionUtils;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.common.tax.SciNameNormalizer;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexImpl;
import life.catalogue.parser.NameParser;

import org.gbif.nameparser.api.Rank;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;

/**
 * Matches usages against given candidates which are supplied by the to be implemented usagesByCanonicalNidx method.
 * Matching is primarily based on names index matches,
 * but implements some further logic for canonical names and cross code homonyms.
 *
 * This class contains the pure matching code and does not do any persistence or retrieval.
 */
public abstract class UsageMatcher {
  private final static Logger LOG = LoggerFactory.getLogger(UsageMatcher.class);
  private final int datasetKey;
  private final NameIndex nameIndex;
  private final AuthorComparator authComp;
  private final TaxGroupAnalyzer groupAnalyzer;

  /**
   * @param nidx a canonical names index id
   * @return list of matching usages that act as candidates for the match
   */
  abstract List<SimpleNameClassified<SimpleNameCached>> usagesByCanonicalNidx(int nidx);

  public UsageMatcher(int datasetKey, NameIndex nameIndex) {
    this.datasetKey = datasetKey;
    this.nameIndex = Preconditions.checkNotNull(nameIndex);
    if (nameIndex instanceof NameIndexImpl) {
      this.authComp = ((NameIndexImpl)nameIndex).getAuthComp();
    } else {
      this.authComp = new AuthorComparator(AuthorshipNormalizer.INSTANCE);
    }
    this.groupAnalyzer = new TaxGroupAnalyzer();
  }

  /**
   * Matches the given usage by looking up candidates by their canonical names index id
   * and then filtering them by various properties and the parent classification.
   * @param nu usage to match. A matched classification should be included
   * @return the usage match, an empty match if not existing (yet) or an unsupported match in case of names not included in the names index
   */
  public UsageMatch match(SimpleNameClassified<SimpleNameCached> nu, boolean allowInserts, boolean verbose) throws NotFoundException {
    if (nu.getCanonicalId() == null) {
      return allowInserts ? UsageMatch.unsupported(datasetKey) : UsageMatch.empty(datasetKey, nu.getNamesIndexMatchType());
    }
    var existing = usagesByCanonicalNidx(nu.getCanonicalId());
    if (existing != null && !existing.isEmpty()) {
      // we modify the existing list, so use a copy
      var match = filterCandidates(nu, new ArrayList<>(existing), verbose);
      if (match.isMatch()) {
        // decide about usage match type - the match type we have so far is from names index matching only!
        if (match.type == MatchType.VARIANT || match.type == MatchType.EXACT) {
          String label = SciNameNormalizer.normalizeWhitespaceAndPunctuation(nu.getLabel());
          String matchLabel = SciNameNormalizer.normalizeWhitespaceAndPunctuation(match.usage.getLabel());
          if (match.type == MatchType.VARIANT && matchLabel.equals(label)) {
            match = new UsageMatch(match, MatchType.EXACT);
          } else if (match.type == MatchType.EXACT && !matchLabel.equals(label)) {
            match = new UsageMatch(match, MatchType.VARIANT);
          }
        }
        // CANONICAL match type remains
        // no matches also: AMBIGUOUS, NONE, UNSUPPORTED
      }
      return match;
    }
    return UsageMatch.empty(datasetKey);
  }

  private static boolean ranksDiffer(Rank r1, Supplier<Optional<Rank>> r1pSupplier, Rank r2, List<SimpleNameCached> r2parents) {
    var eq = RankComparator.compare(r1, r2);
    if (eq == Equality.UNKNOWN) {
      if (r1 == Rank.UNRANKED || r2 == Rank.UNRANKED) {
        // difficult. Some cases like Biota (genus) should not match Biota (unranked) = Life
        // others like an unranked genus should match to its genus.
        // we compare the next concrete parent rank instead to make sure we dont see invalid rank orders and avoid the Biota match
        Rank concreteRank = r1 == Rank.UNRANKED ? r2 : r1;
        Optional<Rank> rankParent = r1 == Rank.UNRANKED ? r1pSupplier.get() : r2parents.stream()
          .map(SimpleName::getRank)
          .filter(r -> !r.isUncomparable())
          .findFirst();
        return !rankParent.isPresent() || rankParent.get().lowerThan(concreteRank);
      } else if (r1.isUncomparable() || r2.isUncomparable()) {
        // we want subspecies & infraspecific or subgenus & infrageneric name not to differ
        return false;
      }
    }
    return eq == Equality.DIFFERENT;
  }

  private static void updateAlt(List<SimpleNameClassified<SimpleNameCached>> alt, List<SimpleNameClassified<SimpleNameCached>> existingWithCl) {
    if (alt != null && existingWithCl != null) {
      Map<String, SimpleNameClassified<SimpleNameCached>> exByKey = new HashMap<>();
      for (var snc : existingWithCl) {
        exByKey.put(snc.getId(), snc);
      }
      for (int i = 0; i < alt.size(); i++) {
        var snc = exByKey.get( alt.get(i).getId() );
        if (snc != null) {
          alt.set(i, snc);
        }
      }
    }
  }

  /**
   * @param nu         usage to be match
   * @param existing   candidates with the same names index id to be matched against
   * @return single match
   * @throws NotFoundException if parent classifications do not resolve
   */
  private UsageMatch filterCandidates(SimpleNameClassified<SimpleNameCached> nu, List<SimpleNameClassified<SimpleNameCached>> existing, boolean verbose) throws NotFoundException {
    final boolean qualifiedName = nu.hasAuthorship() && nu.getRank() != null && nu.getRank() != Rank.UNRANKED;

    // if set to true during filtering the final match will be a snap, not a true match
    boolean snap = false;

    // if we need to set alternatives keep them before we modify the candidates list
    final List<SimpleNameClassified<SimpleNameCached>> alt = verbose ? List.copyOf(existing) : null;

    // make sure we never have bare names - we want usages!
    existing.removeIf(u -> u.getStatus().isBareName());

    // only allow potentially matching ranks if a rank was supplied (external queries often have no rank!)
    // name match requests from outside often come with no rank
    // we dont want them to be filtered by rank, so we allow unranked
    if (nu.getRank() != null && nu.getRank() != Rank.UNRANKED) {
      existing.removeIf(u -> ranksDiffer(u.getRank(), () -> concreteParentRank(u), nu.getRank(), nu.getClassification()));
      // require strict rank match in case it exists at least once
      if (existing.size() > 1 && contains(existing, nu.getRank())) {
        existing.removeIf(u -> u.getRank() != nu.getRank());
      }
    }

    // remove canonical matches between 2 qualified, non suprageneric names
    // for genus matches we keep the canonical matches and compare their family further down
    if (qualifiedName && !nu.getRank().isGenusOrSuprageneric()) {
      existing.removeIf(u -> u.hasAuthorship()
        && !u.getNamesIndexId().equals(nu.getNamesIndexId()) // nidx encodes the exact rank,
        // ... but we want uncomparable ranks to potentially match, e.g. infraspecific_name & subspecies
        && (u.getRank() == nu.getRank()
          || ((u.getRank().isUncomparable() || nu.getRank().isUncomparable()) && !sameNidxWithoutRank(u, nu))
        )
      );
    }

    // remove canonical matches between 2 qualified genus names, UNLESS they are in the exact same family!
    if (qualifiedName && nu.getRank() == Rank.GENUS) {
      existing.removeIf(u -> u.hasAuthorship() && !u.getNamesIndexId().equals(nu.getNamesIndexId()) && !sameFamily(u, nu.getClassification()));
      // snap if there is just one genus left?
      snap = !existing.isEmpty() && existing.stream()
        .allMatch(u -> u.hasAuthorship() && !u.getNamesIndexId().equals(nu.getNamesIndexId()));
    }

    // shortcut if no candidates are left
    if (existing.isEmpty()) {
      return UsageMatch.empty(MatchType.NONE, alt, datasetKey);
    }

    // Avoid tax group comparison for supragenerics if they both are properly accepted
    // no homonyms above genus level unless given in configured homonym sources (e.g. backbone patch, col)
    // snap to that single higher taxon right away!
    if (nu.getRank() != null && nu.getRank().isSuprageneric() && existing.size() == 1 &&
        nu.getStatus() == TaxonomicStatus.ACCEPTED && existing.get(0).getStatus() == TaxonomicStatus.ACCEPTED
    ) {
      LOG.debug("Avoid tax group filtering for accepted suprageneric {} {} with single match", nu.getRank(), nu.getLabel());
    } else {
      // tax group matching based on classification for everything else!
      // replace alternatives with instances that have a classification
      updateAlt(alt, existing);
      // check classification for all others
      if (nu.hasClassification() && !existing.isEmpty()) {
        // trim parents when a marker exists
        var parentsCopy = nu.getClassification();
        var markerIdx = CollectionUtils.lastIndexOf(nu.getClassification(), sn -> sn.marked);
        if (markerIdx >= 0) {
          parentsCopy = nu.getClassification().subList(markerIdx, nu.getClassification().size());
        }
        var group = groupAnalyzer.analyze(nu, parentsCopy);
        if (existing.removeIf(rn -> !classificationMatches(group, rn))) {
          LOG.debug("Removed matches for {} usage {} with classifications not in {} group", nu.getRank(), nu.getLabel(), group);
        }
      }
    }

    // shortcut if no candidates are left
    if (existing.isEmpty()) {
      return UsageMatch.empty(MatchType.NONE, alt, datasetKey);
    }

    // remove non matching codes if more than 1 exist
    // there are issues with ambiregnal taxa and mixed codes and we would create many duplicates otherwise
    if (nu.getRank().isSupraspecific() && existing.size() > 1 && nu.getCode() != null) {
      existing.removeIf(u -> {
        var rem = u.getCode() != null && u.getCode() != nu.getCode();
        if (rem) {
          LOG.debug("Removed matches for {} usage {} [code={}] having a different code {}", nu.getRank(), nu.getLabel(), nu.getCode(), u.getCode());
        }
        return rem;
      });
    }

    // first try exact single match with authorship - dont remove matches from the candidate list!
    if (qualifiedName) {
      boolean matchExact = false;
      boolean onlyUseIfExact = false;
      SimpleNameClassified<SimpleNameCached> match = null;
      for (var u : existing) {
        if (u.getNamesIndexId().equals(nu.getNamesIndexId())) {
          boolean exact = u.getLabel().equalsIgnoreCase(nu.getLabel());
          if (match == null) {
            match = u;
            matchExact = exact;
          } else {
            // there are multiple matches. Maybe just one matches the exact same name string?
            if (exact && matchExact) {
              LOG.info("Exact homonyms existing for {} {}", nu.getRank(), nu.getLabel());
              match = null;
              break;
            } else if (exact){
              // this is an exact match, but previous one was not, so use this match instead
              match = u;
              matchExact = true;
            } else if(matchExact) {
              // this is no exact match, but previous one was, so keep it
            } else {
              // this and previous match was not exact. Dont use any match, but continue to look for exact match
              onlyUseIfExact = true;
            }
          }
        }
      }
      // dont use the match if it was ambiguous before and isn't exact
      if (onlyUseIfExact && !matchExact) {
        match = null;
      }
      if (match != null) {
        return UsageMatch.match(match, datasetKey, alt);
      }
    }

    // qualified matches require authorship AND rank
    // we might not have a rank, but an authorship alone. Test for it and remove other authorships
    if (existing.size() > 1 && !qualifiedName && nu.hasAuthorship()) {
      final var nuSci = parseSciName(nu);
      existing.removeIf(u -> {
        if (u.hasAuthorship()) {
          var uSci = parseSciName(u);
          if (uSci.hasAuthorship()) {
            return authComp.compare(uSci, nuSci) == Equality.DIFFERENT;
          }
        }
        return false;
      });
    }

    // return a match if we have exactly one candidate left!
    if (existing.size() == 1) {
      if (snap) {
        return UsageMatch.snap(existing.get(0), datasetKey, alt);
      }
      return UsageMatch.match(existing.get(0), datasetKey, alt);
    }

    // we have at least 2 match candidates here, maybe more
    // prefer a single match with authorship!
    long canonMatches = existing.stream().filter(u -> !u.hasAuthorship()).count();
    if (qualifiedName && existing.size() - canonMatches == 1) {
      for (var u : existing) {
        if (u.hasAuthorship()) {
          return UsageMatch.match(u, datasetKey, alt);
        }
      }
    }

    // all synonyms pointing to the same accepted? then it won't matter much for snapping
    SimpleNameClassified<SimpleNameCached> synonym = null;
    String parentID = null;
    for (var u : existing) {
      if (u.getStatus().isTaxon()) {
        synonym = null;
        break;
      }
      if (parentID == null) {
        parentID = u.getParent();
        synonym = u;
      } else if (!parentID.equals(u.getParent())) {
        synonym = null;
        break;
      }
    }
    if (synonym != null) {
      return UsageMatch.snap(synonym, datasetKey, alt);
    }

    // remove provisional usages
    existing.removeIf(u -> u.getStatus() == TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    if (existing.size() == 1) {
      return UsageMatch.snap(existing.get(0), datasetKey, alt);
    }

    if (existing.isEmpty()) {
      return UsageMatch.empty(MatchType.NONE, alt, datasetKey);

    } else {
      // compare exact classification, not just group, and match to best=lowest rank possible
      Rank lowest = null;
      SimpleNameClassified<SimpleNameCached> best = null;
      for (var ex : existing) {
        var lowestMatch = lowestClassificationMatch(ex, nu.getClassification());
        if (lowestMatch != null) {
          if (lowest == null || lowest.higherThan(lowestMatch)) {
            best = ex;
            lowest = lowestMatch;
          } else if (lowest == lowestMatch) {
            // same ranks, reset best match
            best = null;
          }
        }
      }
      if (best != null) {
        LOG.debug("{} ambiguous matches encountered for {} in source {}, picking closest classified usage with rank {}", existing.size(), nu.getLabel(), datasetKey, lowest);
        return UsageMatch.match(MatchType.AMBIGUOUS, best, datasetKey, alt);
      }

      // prefer accepted over synonyms
      long accMatches = existing.stream().filter(u -> u.getStatus().isTaxon()).count();
      if (accMatches == 1) {
        existing.removeIf(u -> !u.getStatus().isTaxon());
        LOG.debug("{} ambiguous homonyms encountered for {} in source {}, picking single accepted name", existing.size(), nu.getLabel(), datasetKey);
        return UsageMatch.snap(existing.get(0), datasetKey, alt);
      }

      // now look for the candidate with the lowest classification - no matter if it matches
      lowest = null;
      best = null;
      for (var ex : existing) {
        if (ex.getClassification() != null && !ex.getClassification().isEmpty()) {
          var lowestMatch = ex.getClassification().get(0).getRank();
          if (lowestMatch.notOtherOrUnranked()) {
            if (lowest == null || lowest.higherThan(lowestMatch)) {
              best = ex;
              lowest = lowestMatch;
            } else if (lowest == lowestMatch) {
              // same ranks, reset best match
              best = null;
            }
          }
        }
      }
      if (best != null) {
        LOG.debug("{} ambiguous matches encountered for {}, picking lowest classified usage with rank {}", existing.size(), nu.getLabel(), lowest);
        return UsageMatch.match(MatchType.AMBIGUOUS, best, datasetKey, alt);
      }

      LOG.debug("{} ambiguous names matched for {}. Pick randomly", existing.size(), nu.getLabel());
      return UsageMatch.match(MatchType.AMBIGUOUS, existing.get(0), datasetKey, alt);
    }
  }
  private ScientificName parseSciName(SimpleName sn) {
    Name n = new Name();
    try {
      var optAuthor = NameParser.PARSER.parseAuthorship(sn.getAuthorship());
      if (optAuthor.isPresent()) {
        var a = optAuthor.get();
        n.setCombinationAuthorship(a.getCombinationAuthorship());
        n.setBasionymAuthorship(a.getBasionymAuthorship());
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
    return n;
  }

  private Optional<Rank> concreteParentRank(SimpleNameClassified<SimpleNameCached> cl) {
    return cl.getClassification() == null ? Optional.empty() : cl.getClassification().stream()
      .map(SimpleName::getRank)
      .filter(r -> !r.isUncomparable())
      .findFirst();
  }

  /**
   * Rematches with the same rank to see if nidx still differ
   * @return true if the names, applied with the same rank, match to the same nidx
   */
  private boolean sameNidxWithoutRank(SimpleNameCached u, SimpleNameCached name) {
    var volName = new Name(name); // we copy the instance to not change the original
    volName.setRank(u.getRank());
    var match = nameIndex.match(volName, false, false);
    return match.hasMatch() && Objects.equals(u.getNamesIndexId(), match.getNameKey());
  }

  private boolean sameFamily(SimpleNameClassified<SimpleNameCached> u, List<SimpleNameCached> parents) {
    var fam1 = u.getClassification().stream().filter(n -> n.getRank()==Rank.FAMILY).findFirst();
    var fam2 = parents.stream().filter(n -> n.getRank()==Rank.FAMILY).findFirst();
    return fam1.isPresent() && fam2.isPresent() && fam1.get().getName().equalsIgnoreCase(fam2.get().getName());
  }

  private Rank lowestClassificationMatch(SimpleNameClassified<SimpleNameCached> candidate, List<SimpleNameCached> parents) {
    Rank lowest = null;
    if (parents != null) {
      for (var p : parents) {
        // does the exact same name & rank exist in the parents list?
        if (p.getRank() != null && p.getRank().notOtherOrUnranked()) {
          var cp = candidate.getByRank(p.getRank());
          if (cp != null && cp.getName().equalsIgnoreCase(p.getName()) && (lowest == null || lowest.higherThan(p.getRank()))) {
            lowest = p.getRank();
          }
        }
      }
    }
    return lowest;
  }

  private static boolean contains(Collection<? extends SimpleNameWithNidx> usages, Rank rank) {
    if (rank != null) {
      for (SimpleNameWithNidx u : usages) {
        if (u.getRank() == rank) {
          return true;
        }
      }
    }
    return false;
  }

  // if authors are missing require the classification to not contradict!
  private boolean classificationMatches(TaxGroup group, SimpleNameClassified<SimpleNameCached> candidate) {
    if (group == null) {
      return true;
    }
    var candidateGroup = groupAnalyzer.analyze(candidate, candidate.getClassification());
    return !group.isDisparateTo(candidateGroup);
  }

}
