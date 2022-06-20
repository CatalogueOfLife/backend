package life.catalogue.assembly;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.LoadingCache;

import com.github.benmanes.caffeine.cache.Weigher;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Page;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.matching.NameIndex;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.checkerframework.checker.index.qual.NonNegative;
import org.checkerframework.checker.nullness.qual.NonNull;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;

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
      var num = session.getMapper(NameUsageMapper.class);
      return num.listByNamesIndexID(datasetKey, integer, page);
    }
  }

  public UsageMatcher(int datasetKey, NameIndex nameIndex, SqlSessionFactory factory) {
    this.datasetKey = datasetKey;
    this.nameIndex = nameIndex;
    this.factory = factory;
  }

  public NameUsageBase match(NameUsageBase nu, List<ParentStack.MatchedUsage> parents) {
    // rematch?
    if (nu.getName().getNamesIndexId() == null) {
      var match = nameIndex.match(nu.getName(), true, false);
      if (match.hasMatch()) {
        nu.getName().setNamesIndexId(match.getName().getKey());
        nu.getName().setNamesIndexType(match.getType());
      } else {
        LOG.info("No name match for {}", nu.getName());
      }
    }
    if (nu.getName().getNamesIndexId() != null) {
      var existing = usages.get(nu.getName().getNamesIndexId());
      if (!existing.isEmpty()) {
        return match(nu, existing, parents);
      }
    }
    return null;
  }

  private NameUsageBase match(NameUsageBase nu, List<NameUsageBase> existing, List<ParentStack.MatchedUsage> parents) {
    //TODO: do it properly
    return existing.get(0);
  }

}
