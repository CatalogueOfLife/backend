package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.exception.UnavailableException;
import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.cache.CacheLoader;
import life.catalogue.cache.UsageCache;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexImpl;

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
  private final MatchingUtils utils;
  // key = datasetKey + canonical nidx
  private final LoadingCache<DSID<Integer>, List<SimpleNameClassified<SimpleNameCached>>> usages = Caffeine.newBuilder()
                                                                                     .maximumSize(100_000)
                                                                                     .build(this::loadUsagesByNidx);
  private final LoadingCache<Integer, UsageMatcher> matcher = Caffeine.newBuilder()
    .maximumSize(100)
    .build(this::createMatcher);

  private UsageMatcher createMatcher(int datasetKey) {
    return new UsageMatcher(datasetKey, nameIndex) {
      @Override
      List<SimpleNameClassified<SimpleNameCached>> usagesByCanonicalNidx(int nidx) {
        return usages.get(DSID.of(datasetKey, nidx));
      }

      @Override
      public List<SimpleNameCached> getClassification(String usageID) throws NotFoundException {
        return uCache.getClassification(DSID.of(datasetKey, usageID), loaders.getOrDefault(datasetKey, defaultLoader));
      }

      @Override
      public void add(SimpleNameCached sn) {
        uCache.put(datasetKey, sn);
      }

      @Override
      public void updateParent(String oldID, String newID) {
        uCache.updateParent(datasetKey, oldID, newID);
      }
    };
  }

  /**
   * @param nidx a names index id wrapped by a datasetKey
   * @return list of matching usages for the requested dataset only
   */
  private List<SimpleNameClassified<SimpleNameCached>> loadUsagesByNidx(@NonNull DSID<Integer> nidx) {
    try (SqlSession session = factory.openSession(true)) {
      var result = session.getMapper(NameUsageMapper.class).listByCanonNIDX(nidx.getDatasetKey(), nidx.getId());
      // avoid empty lists which get cached
      int datasetKey = nidx.getDatasetKey();
      return result == null || result.isEmpty() ? null : result.stream()
        .map(u -> new SimpleNameClassified<>(u,
          u.getParent() == null ? null : uCache.getClassification(u.toParentDSID(datasetKey), loaders.getOrDefault(datasetKey, defaultLoader))
        ))
        .collect(Collectors.toList());
    }
  }

  public UsageMatcherGlobal(NameIndex nameIndex, UsageCache uCache, SqlSessionFactory factory) {
    this.nameIndex = Preconditions.checkNotNull(nameIndex);
    this.factory = Preconditions.checkNotNull(factory);
    this.uCache = uCache;
    this.defaultLoader = new CacheLoader.MybatisLoader(factory);
    this.utils = new MatchingUtils(nameIndex);
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

  public UsageMatcher getMatcher(int datasetKey) {
    return matcher.get(datasetKey);
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

  /**
   * Maps a single usage from a given source to another dataset
   * @param src usage to map
   * @param targetDatasetKey dataset to map to
   */
  public UsageMatch map(DSID<String> src, int targetDatasetKey, boolean verbose) {
    NameUsageBase nu;
    List<SimpleNameCached> classification;
    try (SqlSession session = factory.openSession()) {
      nu = session.getMapper(NameUsageMapper.class).get(src);
      classification = uCache.getClassification(src, loaders.getOrDefault(src.getDatasetKey(), defaultLoader));
    }
    return match(targetDatasetKey, nu, classification, false, verbose);
  }

  /**
   * @param datasetKey the target dataset to match against
   * @param nu usage to match. Requires a name instance to exist
   * @param classification of the usage to be matched
   */
  public UsageMatch match(int datasetKey, NameUsageBase nu, @Nullable List<? extends SimpleName> classification, boolean allowInserts, boolean verbose) {
    classification = ObjectUtils.coalesce(classification, List.of()); // no nulls
    // match classification to names index
    List<SimpleNameCached> parents = new ArrayList<>();
    for (var sn : classification) {
      if (sn.getRank() == Rank.SPECIES) continue; // ignore binomials for now
      Name n = Name.newBuilder()
                   .datasetKey(datasetKey)
                   .rank(sn.getRank())
                   .scientificName(sn.getName())
                   .uninomial(sn.getName())
                   .code(nu.getName().getCode())
                   .build();
      parents.add(utils.toSimpleNameCached(new Taxon(n)));
    }
    return matchWithParents(datasetKey, nu, parents, allowInserts, verbose);
  }

  /**
   * Matches the given usage by looking up candidates by their canonical names index id
   * and then filtering them by various properties and the parent classification.
   * @param datasetKey the target dataset to match against
   * @param nu usage to match. Requires a name instance to exist
   * @param parents classification of the usage to be matched
   * @return the usage match, an empty match if not existing (yet) or an unsupported match in case of names not included in the names index
   */
  public UsageMatch matchWithParents(int datasetKey, NameUsageBase nu, List<SimpleNameCached> parents,
                                     boolean allowInserts, boolean verbose
  ) throws NotFoundException {
    var nidx = utils.nidxAndMatchIfNeeded(nu, allowInserts);
    if (!nidx.hasNidx()) {
      return allowInserts ? UsageMatch.unsupported(datasetKey) : UsageMatch.empty(datasetKey, nidx.matchType);
    }
    var mx = matcher.get(datasetKey);
    var snc = new SimpleNameClassified<SimpleNameCached>(nu.toSimpleNameClassified(nidx.id));
    snc.setClassification(parents);
    return mx.match(snc, allowInserts, verbose);
  }

  public void invalidate(int targetDatasetKey, Integer canonicalId) {
    usages.invalidate(DSID.of(targetDatasetKey, canonicalId));
  }

  /**
   * Manually adds a name usage to the cache. Requires the datasetKey to be set correctly.
   * The name will be matched to the names index if it does not have a names index id yet.
   */
  public SimpleNameClassified<SimpleNameCached> add(NameUsageBase nu, List<SimpleNameCached> parents) {
    Preconditions.checkNotNull(nu.getDatasetKey(), "DatasetKey required to cache usages");
    var canonNidx = utils.nidxAndMatchIfNeeded(nu, true);
    var snc = new SimpleNameClassified<SimpleNameCached>(new SimpleNameCached(nu, canonNidx.id));
    snc.setClassification(parents);
    return add(snc, canonNidx.canonicalDSID(nu.getDatasetKey()));
  }

  public SimpleNameClassified<SimpleNameCached> add(SimpleNameClassified<SimpleNameCached> snc, DSID<Integer> canonNidx) {
    if (canonNidx.getId() != null) {
      var before = usages.get(canonNidx);
      if (before == null) {
        // nothing existing, even after loading the cache from the db. Create a new list
        before = new ArrayList<>();
        usages.put(canonNidx, before);
      }
      before.add(snc);
      return snc;

    } else {
      LOG.debug("No names index key. Cannot add name usage {}", snc.getLabel());
    }
    return null;
  }

  /**
   * Removes a single entry from the matcher cache.
   * If it is not cached yet, nothing will happen.
   * @param nidx any names index id
   */
  public void clearCache(int datasetKey, int nidx) {
    var n = nameIndex.get(nidx);
    if (n != null) {
      if (n.getCanonicalId() != null && !n.isCanonical()) {
        nidx = n.getCanonicalId();
      }
      usages.invalidate(DSID.of(datasetKey, nidx));
    }
  }

  /**
   * Updates the parentID of the cached names belonging to the given datasetKey
   * and having the given oldParentID.
   * @param datasetKey
   * @param oldParentID
   * @param newParentID
   */
  public void updateCacheParent(int datasetKey, String oldParentID, String newParentID) {
    int count = 0;
    for (var entry : usages.asMap().entrySet()) {
      if (entry.getKey().getDatasetKey() == datasetKey) {
        for (var sn : entry.getValue()) {
          if (Objects.equals(oldParentID, sn.getParent())) {
            sn.setParent(newParentID);
            count++;
          }
        }
      }
    }
    LOG.debug("Updated {} usages from datasetKey {} with new parentID {} in the cache", count, datasetKey, newParentID);
  }

  /**
   * Removes all usages from the given dataset from the matcher cache.
   */
  public void clearCache(int datasetKey) {
    int count = 0;
    for (var k : usages.asMap().keySet()) {
      if (datasetKey == k.getDatasetKey()) {
        usages.invalidate(k);
        count++;
      }
    }
    matcher.invalidate(datasetKey);
    LOG.info("Cleared all {} usages for datasetKey {} from the cache", count, datasetKey);
  }

  /**
   * Wipes the entire cache.
   */
  public void clearCache() {
    usages.invalidateAll();
    matcher.invalidateAll();
    uCache.clear();
    LOG.warn("Cleared entire cache");
  }
}
