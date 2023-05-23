package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.assembly.TaxGroupAnalyzer;
import life.catalogue.cache.UsageCache;
import life.catalogue.db.mapper.NameUsageMapper;

import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Preconditions;

import javax.annotation.Nullable;

/**
 * Matches usages against a given dataset. Matching is primarily based on names index matches,
 * but implements some further logic for canonical names and cross code homonyms.
 *
 * Matches are retrieved from the database and are cached in particular for uninomials / higher taxa.
 */
public class UsageMatcherGlobal {
  private final static Logger LOG = LoggerFactory.getLogger(UsageMatcherGlobal.class);
  private final NameIndex nameIndex;
  private final UsageCache uCache;
  private final SqlSessionFactory factory;
  private final TaxGroupAnalyzer groupAnalyzer;
  // key = datasetKey + canonical nidx
  private final LoadingCache<DSID<Integer>, List<SimpleNameWithPub>> usages = Caffeine.newBuilder()
                                                                                         .maximumSize(100_000)
                                                                                         .build(this::loadUsagesByNidx);

  /**
   * @param nidx a names index id wrapped by a datasetKey
   * @return list of matching usages for the requested dataset only
   */
  private List<SimpleNameWithPub> loadUsagesByNidx(@NonNull DSID<Integer> nidx) {
    try (SqlSession session = factory.openSession(true)) {
      var result = session.getMapper(NameUsageMapper.class).listByCanonNIDX(nidx.getDatasetKey(), nidx.getId());
      // avoid empty lists which get cached
      return result == null || result.isEmpty() ? null : result;
    }
  }

  private SimpleNameWithPub loadUsage(@NonNull DSID<String> key) {
    try (SqlSession session = factory.openSession(true)) {
      return session.getMapper(NameUsageMapper.class).getSimplePub(key);
    }
  }

  public UsageMatcherGlobal(NameIndex nameIndex, UsageCache uCache, SqlSessionFactory factory) {
    this.nameIndex = Preconditions.checkNotNull(nameIndex);
    this.factory = Preconditions.checkNotNull(factory);
    this.uCache = uCache;
    this.groupAnalyzer = new TaxGroupAnalyzer();
  }

  /**
   * Determine if all components needed for the matcher are currently online.
   *
   * @throws UnavailableException if some component has not yet started
   **/
  public void assertComponentsOnline() throws UnavailableException {
    nameIndex.assertOnline();
    uCache.assertOnline();
  }

  public UsageCache getUCache() {
    return uCache;
  }

  private DSID<Integer> canonNidx(int datasetKey, Integer nidx) {
    if (nidx != null) {
      var xn = nameIndex.get(nidx);
      if (xn != null) {
        return DSID.of(datasetKey, xn.getCanonicalId());
      }
    }
    return null;
  }

  /**
   * @param datasetKey the target dataset to match against
   * @param nu usage to match. Requires a name instance to exist
   * @param classification of the usage to be matched
   */
  public UsageMatch match(int datasetKey, NameUsageBase nu, @Nullable Classification classification) {
    return match(datasetKey, nu, classification == null ? Collections.emptyList() : classification.asSimpleNames());
  }

  /**
   * @param datasetKey the target dataset to match against
   * @param nu usage to match. Requires a name instance to exist
   * @param classification of the usage to be matched
   */
  public UsageMatch match(int datasetKey, NameUsageBase nu, List<? extends SimpleName> classification) {
    // match classification from top down
    List<ParentStack.MatchedUsage> parents = new ArrayList<>();
    for (var sn : classification) {
      if (sn.getRank() == Rank.SPECIES) continue; // ignore binomials for now
      Name n = Name.newBuilder()
                   .datasetKey(datasetKey)
                   .rank(sn.getRank())
                   .scientificName(sn.getName())
                   .uninomial(sn.getName())
                   .code(nu.getName().getCode())
                   .build();
      Taxon t = new Taxon(n);
      matchNidxIfNeeded(datasetKey, t);
      var mu = new ParentStack.MatchedUsage(toSimpleName(t));
      parents.add(mu);
      var m = matchWithParents(datasetKey, t, parents);
      if (m.isMatch()) {
        mu.match = m.usage;
      }
    }
    return matchWithParents(datasetKey, nu, parents);
  }

  /**
   *
   * @param datasetKey the target dataset to match against
   * @param nu usage to match. Requires a name instance to exist
   * @param parents classification of the usage to be matched
   * @return
   */
  public UsageMatch matchWithParents(int datasetKey, NameUsageBase nu, List<ParentStack.MatchedUsage> parents) {
    var canonNidx = matchNidxIfNeeded(datasetKey, nu);
    if (canonNidx != null) {
      var existing = usages.get(canonNidx);
      if (existing != null && !existing.isEmpty()) {
        return match(datasetKey, nu, existing, parents);
      }
    }
    return UsageMatch.empty(datasetKey);
  }

  public SimpleNameWithNidx toSimpleName(NameUsageBase nu) {
    if (nu != null) {
      var canonNidx = matchNidxIfNeeded(nu.getDatasetKey(), nu);
      return new SimpleNameWithNidx(nu, canonNidx == null ? null : canonNidx.getId());
    }
    return null;
  }

  /**
   * @param datasetKey the dataset key of the DSID to be returned
   * @return the canonical names index id or null if it cant be matched
   */
  private DSID<Integer> matchNidxIfNeeded(int datasetKey, NameUsageBase nu) {
    if (nu.getName().getNamesIndexId() == null) {
      var match = nameIndex.match(nu.getName(), true, false);
      if (match.hasMatch()) {
        nu.getName().setNamesIndexId(match.getName().getKey());
        nu.getName().setNamesIndexType(match.getType());
        // we know the canonical id, return it right here
        return DSID.of(datasetKey, match.getName().getCanonicalId());

      } else {
        LOG.info("No name match for {}", nu.getName());
      }
    }
    return canonNidx(datasetKey, nu.getName().getNamesIndexId());
  }

  /**
   * @param datasetKey the target dataset to match against
   * @param nu usage to be match
   * @param existing candidates to be matched against
   * @param parents classification of the usage to be matched
   * @return single match
   */
  private UsageMatch match(int datasetKey, NameUsageBase nu, List<SimpleNameWithPub> existing, List<ParentStack.MatchedUsage> parents) {
    final boolean qualifiedName = nu.getName().hasAuthorship();

    // make sure we never have bare names - we want usages!
    existing.removeIf(u -> u.getStatus().isBareName());

    // wipe out bad ranks if we have multiple matches
    if (existing.size() > 1 && nu.getRank() != null && contains(existing, nu.getRank())) {
      existing.removeIf(u -> u.getRank() != nu.getRank());
    }

    // remove canonical matches between 2 qualified, non suprageneric names
    if (qualifiedName && !nu.getRank().isSuprageneric()) {
      existing.removeIf(u -> u.hasAuthorship() && !u.getNamesIndexId().equals(nu.getName().getNamesIndexId()));
    }

    // from here on we need the classification of all candidates
    final var existingWithCl = existing.stream()
                                 .map(ex -> uCache.withClassification(datasetKey, ex, this::loadUsage))
                                 .collect(Collectors.toList());

    if (nu.getRank().isSuprageneric() && existingWithCl.size() == 1) {
      // no homonyms above genus level unless given in configured homonym sources (e.g. backbone patch, col)
      // snap to that single higher taxon right away!

    } else if (nu.getRank().isSuprageneric() && existingWithCl.size() > 1){
      return matchSupragenerics(datasetKey, existingWithCl, parents);

    } else {
      // check classification for all others
      if (parents != null) {
        List<SimpleName> parentsSN = parents.stream()
                                        .map(p -> p.usage)
                                        .collect(Collectors.toList());
        var group = groupAnalyzer.analyze(nu.toSimpleNameLink(), parentsSN);
        LOG.debug("Only consider matches for usage {} with classifications in {} group", nu.getName().getLabelWithRank(), group);
        existingWithCl.removeIf(rn -> !classificationMatches(group, rn));
      }
    }

    // first try exact single match with authorship
    if (qualifiedName) {
      SimpleNameClassified<SimpleNameWithPub> match = null;
      for (var u : existingWithCl) {
        if (u.getNamesIndexId().equals(nu.getName().getNamesIndexId())) {
          if (match != null) {
            LOG.warn("Exact homonyms existing in dataset {} for {}", datasetKey, nu.getName().getLabelWithRank());
            match = null;
            break;
          } else {
            match = u;
          }
        }
      }
      if (match != null) {
        return UsageMatch.match(match, datasetKey);
      }
    }

    if (existingWithCl.size() == 1) {
      return UsageMatch.match(existingWithCl.get(0), datasetKey);
    }

    // we have at least 2 match candidates here, maybe more
    // prefer a single match with authorship!
    long canonMatches = existingWithCl.stream().filter(u -> !u.hasAuthorship()).count();
    if (qualifiedName && existingWithCl.size() - canonMatches == 1) {
      for (var u : existingWithCl) {
        if (u.hasAuthorship()) {
          return UsageMatch.match(u, datasetKey);
        }
      }
    }

    // all synonyms pointing to the same accepted? then it won't matter much for snapping
    SimpleNameClassified<SimpleNameWithPub> synonym = null;
    String parentID = null;
    for (var u : existingWithCl) {
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
      return UsageMatch.snap(synonym, datasetKey);
    }

    // remove provisional usages
    existingWithCl.removeIf(u -> u.getStatus() == TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    if (existingWithCl.size() == 1) {
      return UsageMatch.snap(existingWithCl.get(0), datasetKey);
    }

    // prefer accepted over synonyms
    long accMatches = existingWithCl.stream().filter(u -> u.getStatus().isTaxon()).count();
    if (accMatches == 1) {
      existingWithCl.removeIf(u -> !u.getStatus().isTaxon());
      LOG.debug("{} ambiguous homonyms encountered for {} in source {}, picking single accepted name", existingWithCl.size(), nu.getLabel(), datasetKey);
      return UsageMatch.snap(existingWithCl.get(0), datasetKey);
    }

    LOG.debug("{} ambiguous homonyms encountered for {} in source {}, picking accepted name randomly", existingWithCl.size(), nu.getLabel(), datasetKey);
    return UsageMatch.empty(existingWithCl, datasetKey);
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
  private boolean classificationMatches(TaxGroup group, SimpleNameClassified<SimpleNameWithPub> candidate) {
    if (group == null) {
      return true;
    }
    var candidateGroup = groupAnalyzer.analyze(candidate, candidate.getClassification());
    return !group.isDisparateTo(candidateGroup);
  }

  /**
   * The classification comparison below is rather strict
   * require a match to one of the higher rank homonyms (the old code even did not allow for higher rank homonyms at all!)
   */
  private UsageMatch matchSupragenerics(int datasetKey, List<SimpleNameClassified<SimpleNameWithPub>> homonyms, List<ParentStack.MatchedUsage> parents) {
    if (parents == null || parents.isEmpty()) {
      // pick first
      var first = homonyms.get(0);
      LOG.debug("No parent given for homomym match {}. Pick first", first);
      return UsageMatch.match(MatchType.AMBIGUOUS, first, datasetKey);
    }
    // count number of equal parent names and pick most matching homonym by comparing canonical names index ids
    Set<Integer> parentCNidx = parents.stream()
                                      .map(p -> p.match == null ? p.usage.getCanonicalId() : p.match.getCanonicalId())
                                      .filter(Objects::nonNull)
                                      .collect(Collectors.toSet());
    SimpleNameClassified<SimpleNameWithPub> best = homonyms.get(0);
    int max = 0;
    for (var hom : homonyms) {
      Set<Integer> cNidx = hom.getClassification().stream()
                                        .map(SimpleNameWithNidx::getCanonicalId)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toSet());
      cNidx.retainAll(parentCNidx);
      if (cNidx.size() > max) {
        best = hom;
        max = cNidx.size();
      }
    }
    return UsageMatch.match(MatchType.AMBIGUOUS, best, datasetKey);
  }

  /**
   * Manually adds a name usage to the cache. Requires the datasetKey to be set correctly.
   * The name will be matched to the names index if it does not have a names index id yet.
   */
  public SimpleNameWithPub add(NameUsageBase nu) {
    Preconditions.checkNotNull(nu.getDatasetKey(), "DatasetKey required to cache usages");
    var canonNidx = matchNidxIfNeeded(nu.getDatasetKey(), nu);
    if (canonNidx != null) {

      var before = usages.get(canonNidx);
      if (before == null) {
        // nothing existing, even after loading the cache from the db. Create a new list
        before = new ArrayList<>();
        usages.put(canonNidx, before);
      }
      var sn = new SimpleNameWithPub(nu, canonNidx.getId());
      before.add(sn);
      return sn;

    } else {
      LOG.debug("No names index key. Cannot add name usage {}", nu);
    }
    return null;
  }

  /**
   * Removes a single entry from the matcher cache.
   * If it is not cached yet, nothing will happen.
   * @param nidx any names index id
   */
  public void clear(int datasetKey, int nidx) {
    var n = nameIndex.get(nidx);
    if (n != null) {
      if (n.getCanonicalId() != null && !n.isCanonical()) {
        nidx = n.getCanonicalId();
      }
      usages.invalidate(DSID.of(datasetKey, nidx));
    }
  }

  /**
   * Removes all usages from the given dataset from the matcher cache.
   */
  public void clear(int datasetKey) {
    int count = 0;
    for (var k : usages.asMap().keySet()) {
      if (datasetKey == k.getDatasetKey()) {
        usages.invalidate(k);
        count++;
      }
    }
    LOG.info("Cleared all {} usages for datasetKey {} from the cache", count, datasetKey);
  }

  /**
   * Wipes the entire cache.
   */
  public void clear() {
    usages.invalidateAll();
    LOG.warn("Cleared entire cache");
  }
}
