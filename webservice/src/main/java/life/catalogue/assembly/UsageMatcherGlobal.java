package life.catalogue.assembly;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import com.google.common.base.Preconditions;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.matching.NameIndex;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.NonNull;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

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

  public UsageMatcherGlobal(NameIndex nameIndex, SqlSessionFactory factory) {
    this.nameIndex = Preconditions.checkNotNull(nameIndex);
    this.factory = Preconditions.checkNotNull(factory);
    this.uCache = UsageCache.hashMap();
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
   *
   * @param datasetKey the target dataset to match against
   * @param nu usage to match. Requires a name instance to exist
   * @param parents classification of the usage to be matched
   * @return
   */
  public UsageMatch match(int datasetKey, NameUsageBase nu, List<ParentStack.MatchedUsage> parents) {
    var canonNidx = matchNidxIfNeeded(datasetKey, nu);
    if (canonNidx != null) {
      var existing = usages.get(canonNidx);
      if (existing != null && !existing.isEmpty()) {
        return match(datasetKey, nu, existing, parents);
      }
    }
    return UsageMatch.empty(datasetKey);
  }

  SimpleNameWithNidx toSimpleName(NameUsageBase nu) {
    var canonNidx = matchNidxIfNeeded(nu.getDatasetKey(), nu);
    return new SimpleNameWithNidx(nu, canonNidx.getId());
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
      existingWithCl.removeIf(rn -> !classificationMatches(nu, parents, datasetKey, rn));
    }

    // first try exact single match with authorship
    if (qualifiedName) {
      SimpleNameClassified match = null;
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
    SimpleNameClassified synonym = null;
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
      var u = existingWithCl.get(0);
      return UsageMatch.snap(u, datasetKey);
    }

    // finally pick the first accepted with the largest subtree ???
    SimpleNameClassified curr = null;
    long maxDescendants = -1;
    for (var u : existingWithCl) {
      if (u.getStatus().isTaxon()) {
        long descendants = countDescendants(u);
        if (maxDescendants < descendants) {
          maxDescendants = descendants;
          curr = u;
        }
      }
    }
    if (curr != null) {
      LOG.info("{} ambiguous homonyms encountered for {} in source {}, picking largest taxon", existingWithCl.size(), nu.getLabel(), datasetKey);
      return UsageMatch.snap(MatchType.AMBIGUOUS, curr, datasetKey);
    }

    // could not match
    return UsageMatch.empty(datasetKey);
  }

  private long countDescendants(SimpleNameWithNidx u) {
    // TODO: implement
    return 0;
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
  private boolean classificationMatches(NameUsageBase nu, List<ParentStack.MatchedUsage> parents, int datasetKey, SimpleNameClassified candidate) {
    return true;
    //if (currNubParent != null &&
    //    (currNubParent.equals(incertaeSedis)
    //     || existsInClassification(match.node, currNubParent.node, true)
    //     || noClassificationContradiction(match.node, currNubParent.node)
    //    )) {
    //  return true;
    //}
    //return false;
  }

  /**
   * The classification comparison below is rather strict
   * require a match to one of the higher rank homonyms (the old code even did not allow for higher rank homonyms at all!)
   */
  private UsageMatch matchSupragenerics(int datasetKey, List<SimpleNameClassified> homonyms, List<ParentStack.MatchedUsage> parents) {
    if (parents == null || parents.isEmpty()) {
      // pick first
      var first = homonyms.get(0);
      LOG.debug("No parent given for homomym match {}. Pick first", first);
      return UsageMatch.match(MatchType.AMBIGUOUS, first, datasetKey);
    }
    //TODO: remove and implement the homonym disambiguation based on parents!!!
    return UsageMatch.match(MatchType.AMBIGUOUS, homonyms.get(0), datasetKey);

//    List<Homonym> homs = homonyms.stream()
//                                 .map(u -> new Homonym(u, new HashSet<>(parents(u.node))))
//                                 .collect(Collectors.toList());
//    // remove shared nodes, i.e. nodes that exist at least twice
//    Map<Node, AtomicInteger> counts = new HashMap<>();
//    for (Homonym h : homs) {
//      for (Node n : h.nodes) {
//        if (!counts.containsKey(n)) {
//          counts.put(n, new AtomicInteger(1));
//        } else {
//          counts.get(n).incrementAndGet();
//        }
//      }
//    }
//    Set<Node> nonUnique = counts.entrySet().stream()
//                                .filter(e -> e.getValue().get() > 1)
//                                .map(Map.Entry::getKey)
//                                .collect(Collectors.toSet());
//    for (Homonym h : homs) {
//      h.nodes.removeAll(nonUnique);
//    }
//    // now the node list for each homonym contains only unique discriminators
//    // see if we can find any
//    List<Node> parentsInclCurr = new ArrayList<>();
//    parentsInclCurr.add(currNubParent.node);
//    parentsInclCurr.addAll(parents(currNubParent.node));
//    for (Node p : parentsInclCurr) {
//      for (Homonym h : homs) {
//        if (h.nodes.contains(p)) {
//          return h.usage;
//        }
//      }
//    }
//    // nothing unique found, just pick the first
//    NubUsage match = homonyms.get(0);
//    LOG.debug("No unique higher homomym match found for {}. Pick first", match);
//    return match;
  }

  /**
   * Manually adds a name usage to the cache. Requires the datasetKey to be set correctly.
   * The name will be matched to the names index if it does not have a names index id yet.
   */
  public SimpleNameWithNidx add(NameUsageBase nu) {
    Preconditions.checkNotNull(nu.getDatasetKey(), "DatasetKey required to cache usages");
    var canonNidx = matchNidxIfNeeded(nu.getDatasetKey(), nu);
    if (canonNidx != null) {

      var before = usages.get(canonNidx);
      if (before == null) {
        // nothing existing, even after loading the cache from the db. Create a new list
        before = new ArrayList<>();
        usages.put(canonNidx, before);
      }
      var sn = new SimpleNameWithPub(nu, canonNidx.getId(), nu.getName().getPublishedInId());
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
