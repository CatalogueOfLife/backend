package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.assembly.TaxGroupAnalyzer;
import life.catalogue.cache.CacheLoader;
import life.catalogue.cache.UsageCache;
import life.catalogue.db.mapper.NameUsageMapper;

import org.gbif.nameparser.api.Rank;

import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.base.Preconditions;

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
  private final CacheLoader defaultLoader;
  private final Map<Integer, CacheLoader> loaders = new HashMap<>();
  private final SqlSessionFactory factory;
  private final TaxGroupAnalyzer groupAnalyzer;
  // key = datasetKey + canonical nidx
  private final LoadingCache<DSID<Integer>, List<SimpleNameCached>> usages = Caffeine.newBuilder()
                                                                                     .maximumSize(100_000)
                                                                                     .build(this::loadUsagesByNidx);

  /**
   * @param nidx a names index id wrapped by a datasetKey
   * @return list of matching usages for the requested dataset only
   */
  private List<SimpleNameCached> loadUsagesByNidx(@NonNull DSID<Integer> nidx) {
    try (SqlSession session = factory.openSession(true)) {
      var result = session.getMapper(NameUsageMapper.class).listByCanonNIDX(nidx.getDatasetKey(), nidx.getId());
      // avoid empty lists which get cached
      return result == null || result.isEmpty() ? null : result;
    }
  }

  public UsageMatcherGlobal(NameIndex nameIndex, UsageCache uCache, SqlSessionFactory factory) {
    this.nameIndex = Preconditions.checkNotNull(nameIndex);
    this.factory = Preconditions.checkNotNull(factory);
    this.uCache = uCache;
    this.defaultLoader = new CacheLoader.MybatisFactory(factory);
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

  public NameIndex getNameIndex() {
    return nameIndex;
  }

  /**
   * Registers a usage loader for the specific dataset to be used instead of the default one which opens a new database session each time
   * @param datasetKey
   * @param loader
   */
  public void registerLoader(int datasetKey, CacheLoader loader) {
    LOG.info("Registering new usage loader for dataset {}: {}", datasetKey, loader.getClass());
    loaders.put(datasetKey, loader);
  }

  public void removeLoader(int datasetKey) {
    LOG.info("Remove usage loader for dataset {}", datasetKey);
    loaders.remove(datasetKey);
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
    List<MatchedParentStack.MatchedUsage> parents = new ArrayList<>();
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
      var mu = new MatchedParentStack.MatchedUsage(toSimpleName(t));
      parents.add(mu);
      var m = matchWithParents(datasetKey, t, parents);
      if (m.isMatch()) {
        mu.match = m.usage;
      }
    }
    return matchWithParents(datasetKey, nu, parents);
  }

  /**
   * Matches the given usage by looking up candidates by their canonical names index id
   * and then filtering them by various properties and the parent classification.
   * @param datasetKey the target dataset to match against
   * @param nu usage to match. Requires a name instance to exist
   * @param parents classification of the usage to be matched
   * @return the usage match, an empty match if not existing (yet) or an unsupported match in case of names not included in the names index
   */
  public UsageMatch matchWithParents(int datasetKey, NameUsageBase nu, List<MatchedParentStack.MatchedUsage> parents) throws NotFoundException {
    var canonNidx = matchNidxIfNeeded(datasetKey, nu);
    if (canonNidx == null) {
      return UsageMatch.unsupported(datasetKey);
    }
    var existing = usages.get(canonNidx);
    if (existing != null && !existing.isEmpty()) {
      // we modify the existing list, so use a copy
      return match(datasetKey, nu, new ArrayList<>(existing), parents);
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

  private static boolean ranksDiffer(Rank r1, Rank r2) {
    var eq = RankComparator.compare(r1, r2);
    if (eq == Equality.UNKNOWN && (r1 == Rank.UNRANKED || r2 == Rank.UNRANKED)) {
      // require suprageneric ranks for unranked matches
      return !(supraGenericOrUnranked(r1) && supraGenericOrUnranked(r2));
    }
    return eq == Equality.DIFFERENT;
  }

  private static boolean supraGenericOrUnranked(Rank r) {
    return r == Rank.UNRANKED || r.isSuprageneric();
  }

  /**
   * @param datasetKey the target dataset to match against
   * @param nu usage to be match
   * @param existing candidates with the same names index id to be matched against
   * @param parents classification of the usage to be matched
   * @return single match
   * @throws NotFoundException if parent classifications do not resolve
   */
  private UsageMatch match(int datasetKey, NameUsageBase nu, List<SimpleNameCached> existing, List<MatchedParentStack.MatchedUsage> parents) throws NotFoundException {
    final boolean qualifiedName = nu.getName().hasAuthorship();

    // make sure we never have bare names - we want usages!
    existing.removeIf(u -> u.getStatus().isBareName());

    // only allow potentially matching ranks
    final Rank rank = nu.getRank() == null ? Rank.UNRANKED : nu.getRank();
    existing.removeIf(u -> ranksDiffer(u.getRank(), rank));
    // require strict rank match in case it exists at least once
    if (existing.size() > 1 && contains(existing, rank)) {
      existing.removeIf(u -> u.getRank() != rank);
    }

    // remove canonical matches between 2 qualified, non suprageneric names
    if (qualifiedName && !nu.getRank().isSuprageneric()) {
      existing.removeIf(u -> u.hasAuthorship() && !u.getNamesIndexId().equals(nu.getName().getNamesIndexId()));
    }

    // from here on we need the classification of all candidates
    var loader = loaders.getOrDefault(datasetKey, defaultLoader);
    final var existingWithCl = existing.stream()
                                 .map(ex -> uCache.withClassification(datasetKey, ex, loader))
                                 .collect(Collectors.toList());

    if (nu.getRank().isSuprageneric() && existingWithCl.size() == 1) {
      // no homonyms above genus level unless given in configured homonym sources (e.g. backbone patch, col)
      // snap to that single higher taxon right away!

    } else if (nu.getRank().isSuprageneric() && existingWithCl.size() > 1){
      return matchSupragenerics(datasetKey, existingWithCl, parents);

    } else {
      // check classification for all others
      if (parents != null && !existingWithCl.isEmpty()) {
        List<SimpleName> parentsSN = parents.stream()
                                        .map(p -> p.usage)
                                        .collect(Collectors.toList());
        var group = groupAnalyzer.analyze(nu.toSimpleNameLink(), parentsSN);
        if (existingWithCl.removeIf(rn -> !classificationMatches(group, rn))) {
          LOG.debug("Removed matches for usage {} with classifications not in {} group", nu.getName().getLabelWithRank(), group);
        }
      }
    }

    // first try exact single match with authorship
    if (qualifiedName) {
      boolean matchExact = false;
      boolean onlyUseIfExact = false;
      SimpleNameClassified<SimpleNameCached> match = null;
      for (var u : existingWithCl) {
        if (u.getNamesIndexId().equals(nu.getName().getNamesIndexId())) {
          boolean exact = u.getLabel().equalsIgnoreCase(nu.getLabel());
          if (match == null) {
            match = u;
            matchExact = exact;
          } else {
            // there are multiple matches. Maybe just one matches the exact same name string?
            if (exact && matchExact) {
              LOG.info("Exact homonyms existing in dataset {} for {}", datasetKey, nu.getName().getLabelWithRank());
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
    SimpleNameClassified<SimpleNameCached> synonym = null;
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

    if (existingWithCl.isEmpty()) {
      return UsageMatch.empty(datasetKey);
    } else {
      LOG.debug("{} ambiguous names matched for {} in source {}", existingWithCl.size(), nu.getLabel(), datasetKey);
      return UsageMatch.empty(existingWithCl, datasetKey);
    }
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

  /**
   * The classification comparison below is rather strict
   * require a match to one of the higher rank homonyms (the old code even did not allow for higher rank homonyms at all!)
   */
  private UsageMatch matchSupragenerics(int datasetKey, List<SimpleNameClassified<SimpleNameCached>> homonyms, List<MatchedParentStack.MatchedUsage> parents) {
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
    SimpleNameClassified<SimpleNameCached> best = homonyms.get(0);
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
  public SimpleNameCached add(NameUsageBase nu) {
    Preconditions.checkNotNull(nu.getDatasetKey(), "DatasetKey required to cache usages");
    var canonNidx = matchNidxIfNeeded(nu.getDatasetKey(), nu);
    if (canonNidx != null) {

      var before = usages.get(canonNidx);
      if (before == null) {
        // nothing existing, even after loading the cache from the db. Create a new list
        before = new ArrayList<>();
        usages.put(canonNidx, before);
      }
      var sn = new SimpleNameCached(nu, canonNidx.getId());
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
