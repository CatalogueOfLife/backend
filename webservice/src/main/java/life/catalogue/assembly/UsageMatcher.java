package life.catalogue.assembly;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Page;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.matching.NameIndex;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.nullness.qual.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Matches usages against a given dataset. Matching is primarily based on names index matches,
 * but implements some further logic for canonical names and cross code homonyms.
 *
 * Matches are retrieved from the database and are cached in particular for uninomials / higher taxa.
 */
public class UsageMatcher {
  private final static Logger LOG = LoggerFactory.getLogger(UsageMatcher.class);
  private final int datasetKey;
  private final NameIndex nameIndex;
  private final SqlSessionFactory factory;
  // key = canonical nidx
  private final LoadingCache<Integer, List<NameUsageBase>> usages = Caffeine.newBuilder()
                                                                                 .maximumSize(100_000)
                                                                                 .build(this::loadUsage);

  private List<NameUsageBase> loadUsage(@NonNull Integer nidx) {
    try (SqlSession session = factory.openSession(true)) {
      var result = session.getMapper(NameUsageMapper.class).listByCanonNIDX(datasetKey, nidx);
      // avoid empty lists which get cached
      return result == null || result.isEmpty() ? null : result;
    }
  }

  public UsageMatcher(int datasetKey, NameIndex nameIndex, SqlSessionFactory factory) {
    this.datasetKey = datasetKey;
    this.nameIndex = nameIndex;
    this.factory = factory;
  }

  private Integer canonNidx(Integer nidx) {
    if (nidx != null) {
      var xn = nameIndex.get(nidx);
      if (xn != null) {
        return xn.getCanonicalId();
      }
    }
    return null;
  }

  public NameUsageBase match(NameUsageBase nu, List<ParentStack.MatchedUsage> parents) {
    var canonNidx = matchNidxIfNeeded(nu);
    if (canonNidx != null) {
      var existing = usages.get(canonNidx);
      if (existing != null && !existing.isEmpty()) {
        return match(nu, existing, parents);
      }
    }
    return null;
  }

  /**
   * @return the canonical names index id or null if it cant be matched
   */
  private Integer matchNidxIfNeeded(NameUsageBase nu) {
    if (nu.getName().getNamesIndexId() == null) {
      var match = nameIndex.match(nu.getName(), true, false);
      if (match.hasMatch()) {
        nu.getName().setNamesIndexId(match.getName().getKey());
        nu.getName().setNamesIndexType(match.getType());
        // we know the canonical id, return it right here
        return match.getName().getCanonicalId();

      } else {
        LOG.info("No name match for {}", nu.getName());
      }
    }
    return canonNidx(nu.getName().getNamesIndexId());
  }

  private NameUsageBase match(NameUsageBase nu, List<NameUsageBase> existing, List<ParentStack.MatchedUsage> parents) {
    //TODO: do it properly
    return existing.get(0);
  }

  /**
   * Evicts all name usages with the given canonical nameIndexID from the cache.
   */
  private void delete(@Nullable Integer cidx) {
    if (cidx != null) {
      usages.invalidate(cidx);
    }
  }

  /**
   * Manually adds a name usage to the cache.
   * The name will be matched to the names index if it does not have a names index id yet.
   */
  public void add(NameUsageBase nu) {
    var canonNidx = matchNidxIfNeeded(nu);
    if (canonNidx != null) {
      var before = usages.get(canonNidx);
      if (before == null) {
        // nothing existing, even after loading the cache from the db. Create a new list
        before = new ArrayList<>();
        usages.put(canonNidx, before);
      }
      before.add(nu);
    } else {
      LOG.debug("No names index key. Cannot add name usage {}", nu);
    }
  }

  public void clear() {
    usages.invalidateAll();
  }
}
