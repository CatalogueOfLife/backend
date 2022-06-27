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
  private final LoadingCache<Integer, List<NameUsageBase>> usages = Caffeine.newBuilder()
                                                                      .maximumSize(100_000)
                                                                      .build(this::loadUsage);
  private final Page page = new Page(0, 100);

  private List<NameUsageBase> loadUsage(@NonNull Integer integer) {
    try (SqlSession session = factory.openSession(true)) {
      var result = session.getMapper(NameUsageMapper.class).listByNamesIndexID(datasetKey, integer, page);
      // avoid empty lists which get cached
      return result == null || result.isEmpty() ? null : result;
    }
  }

  public UsageMatcher(int datasetKey, NameIndex nameIndex, SqlSessionFactory factory) {
    this.datasetKey = datasetKey;
    this.nameIndex = nameIndex;
    this.factory = factory;
  }

  public NameUsageBase match(NameUsageBase nu, List<ParentStack.MatchedUsage> parents) {
    matchNidxIfNeeded(nu);
    if (nu.getName().getNamesIndexId() != null) {
      var nidx = nameIndex.get(nu.getName().getNamesIndexId());

      var existing = usages.get(nu.getName().getNamesIndexId());
      if (existing != null && !existing.isEmpty()) {
        return match(nu, existing, parents);
      }
    }
    return null;
  }

  private void matchNidxIfNeeded(NameUsageBase nu) {
    if (nu.getName().getNamesIndexId() == null) {
      var match = nameIndex.match(nu.getName(), true, false);
      if (match.hasMatch()) {
        nu.getName().setNamesIndexId(match.getName().getKey());
        nu.getName().setNamesIndexType(match.getType());
      } else {
        LOG.info("No name match for {}", nu.getName());
      }
    } else {
      // update verbatimKey with canonical nidx !!!
    }
  }

  private NameUsageBase match(NameUsageBase nu, List<NameUsageBase> existing, List<ParentStack.MatchedUsage> parents) {
    //TODO: do it properly
    return existing.get(0);
  }

  /**
   * Evicts all name usages with the given nameIndexID from the cache.
   */
  private void delete(@Nullable Integer nameIndexID) {
    if (nameIndexID != null) {
      usages.invalidate(nameIndexID);
    }
  }

  /**
   * Manually adds a name usage to the cache.
   * The name will be matched to the names index if it does not have a names index id yet.
   */
  public void add(NameUsageBase nu) {
    matchNidxIfNeeded(nu);
    if (nu.getName().getNamesIndexId() != null) {
      var before = get(nu.getName().getVerbatimKey());
      if (before == null) {
        before = new ArrayList<>();
        before.add(nu);
        usages.put(nu.getName().getNamesIndexId(), before);
      } else {
        before.add(nu);
      }
    } else {
      LOG.debug("No names index key. Cannot add name usage {}", nu);
    }
  }

  public List<NameUsageBase> get(Integer nameIndexID) {
    return usages.get(nameIndexID);
  }

  public void clear() {
    usages.invalidateAll();
  }
}
