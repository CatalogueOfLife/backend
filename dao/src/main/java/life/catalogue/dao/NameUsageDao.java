package life.catalogue.dao;

import jakarta.ws.rs.PathParam;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.db.mapper.NameMatchMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.es.indexing.NameUsageIndexService;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Iterables;

public class NameUsageDao {
  private static final Logger LOG = LoggerFactory.getLogger(NameUsageDao.class);
  private final SqlSessionFactory factory;
  private final NameUsageIndexService indexService;

  public NameUsageDao(SqlSessionFactory factory, NameUsageIndexService indexService) {
    this.factory = factory;
    this.indexService = indexService;
  }

  /**
   * Returns a taxon with the specified key or throws:
   *  - a SynonymException in case the id belongs to a synonym
   *  - a NotFoundException if the id is no name usage at all
   */
  public NameUsageBase get(DSID<String> key) {
    try (SqlSession session = factory.openSession()) {
      return session.getMapper(NameUsageMapper.class).get(key);
    }
  }

  public ResultPage<NameUsageBase> list(int datasetKey, @Nullable String q, Rank rank,
                                        @Nullable String nameID,
                                        @Nullable Integer namesIndexID,
                                        Page page)
  {
    try (SqlSession session = factory.openSession()) {
      Page p = page == null ? new Page() : page;
      NameUsageMapper mapper = session.getMapper(NameUsageMapper.class);
      List<NameUsageBase> result;
      Supplier<Integer> count;
      if (namesIndexID != null) {
        result = mapper.listByNamesIndexOrCanonicalID(datasetKey, namesIndexID, p);
        count = () -> mapper.countByNamesIndexID(namesIndexID, datasetKey);
      } else if (nameID != null) {
        result = mapper.listByNameID(datasetKey, nameID, p);
        count = () -> mapper.countByNameID(nameID, datasetKey);
      } else if (q != null) {
        result = mapper.listByName(datasetKey, q, rank, p);
        count = () -> result.size();
      } else {
        result = mapper.list(datasetKey, p);
        count = () -> mapper.count(datasetKey);
      }
      return new ResultPage<>(p, result, count);
    }
  }

  /**
   * Maximum number of related usages returned. A safeguard for extreme cases like Animalia.
   */
  @VisibleForTesting
  static final int RELATED_LIMIT = 100;

  /**
   * Maximum number of matched names phase 1 considers. Larger than RELATED_LIMIT because a matched name
   * does not have to have a usage - bare names exist - so some matches yield nothing in phase 2.
   */
  @VisibleForTesting
  static final int RELATED_NAME_LIMIT = 3 * RELATED_LIMIT;

  /**
   * Number of datasets a single phase 2 statement asks for. This is the knob that trades locks against
   * round trips: each statement has to open the partitions of all datasets it asks for, so a batch of n
   * datasets locks up to n partition sets, while a smaller batch means more statements.
   */
  @VisibleForTesting
  static final int RELATED_DATASET_BATCH = 10;

  /**
   * Lists related usages from other datasets which are linked via names index matches.
   * Various options to restrict the related datasets to be considered.
   *
   * This runs in two phases on purpose, see listRelatedNames and listRelatedUsages in NameUsageMapper.
   * Phase 1 finds the matching names, phase 2 loads their usages in batches of RELATED_DATASET_BATCH datasets.
   * Phase 2 runs with autocommit so each batch releases its locks instead of accumulating them for the
   * whole request - without that the split saves nothing, as the batches together still reach every partition.
   *
   * @param datasetKey original dataset
   * @param id original usageOD in the above dataset
   * @param gbifOnly if true only datasets with a GBIF key are considered
   * @param nonGbifDatasetKeys optional setting when gbifOnly=true. Set of dataset keys to always consider even if they do not have a gbif key
   * @param datasetTypes optional set of dataset types to consider, ignoring all others
   * @param datasetKeys optional set of dataset keys to consider, ignoring all others
   * @param publisherKeys optional set of dataset GBIF publisher keys to consider, ignoring all others
   * @return at most RELATED_LIMIT usages, never including the given usage itself
   */
  public List<SimpleNameInDataset> related(int datasetKey, String id,
                                     boolean gbifOnly,
                                     @Nullable Collection<Integer> nonGbifDatasetKeys,
                                     @Nullable Collection<DatasetOrigin> datasetOrigins,
                                     @Nullable Collection<DatasetType> datasetTypes,
                                     @Nullable Collection<Integer> datasetKeys,
                                     @Nullable Collection<UUID> publisherKeys) {
    final var key = DSID.of(datasetKey, id);
    final List<DSIDValue<String>> names;
    try (SqlSession session = factory.openSession()) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      num.existsOrThrow(key);
      names = num.listRelatedNames(key, gbifOnly, nonGbifDatasetKeys, datasetOrigins, datasetTypes, datasetKeys,
        publisherKeys, RELATED_NAME_LIMIT);
    }
    if (names.isEmpty()) {
      return List.of();
    }

    // group by dataset so each phase 2 statement only has to open the partitions of its own datasets.
    // sorted so the result order is at least stable across calls
    var byDataset = names.stream().collect(Collectors.groupingBy(DSID::getDatasetKey, TreeMap::new, Collectors.toList()));
    var result = new ArrayList<SimpleNameInDataset>();
    try (SqlSession session = factory.openSession(true)) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      for (var batch : Iterables.partition(byDataset.entrySet(), RELATED_DATASET_BATCH)) {
        var batchKeys = batch.stream().map(Map.Entry::getKey).toList();
        var batchNames = batch.stream().flatMap(e -> e.getValue().stream()).toList();
        for (var u : num.listRelatedUsages(batchKeys, batchNames)) {
          // the origin usage matches its own name, so it always comes back and has to be dropped
          if (u.getDatasetKey() == datasetKey && u.getId().equals(id)) continue;
          result.add(u);
          if (result.size() >= RELATED_LIMIT) {
            return result;
          }
        }
      }
    }
    return result;
  }

  public SimpleName reindex(int datasetKey, String id) {
    SimpleName sn;
    try (var session = factory.openSession()) {
      var num = session.getMapper(NameUsageMapper.class);
      sn = num.getSimple(DSID.of(datasetKey, id));
    }
    indexService.update(datasetKey, id);
    return sn;
  }

  public NameMatch nameMatch(int datasetKey, String id) {
    try (SqlSession session = factory.openSession()) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      NameMatchMapper nm = session.getMapper(NameMatchMapper.class);
      var u = num.get(DSID.of(datasetKey, id));
      if (u != null && u.getName() != null) {
        return nm.get(DSID.of(datasetKey, u.getName().getId()));
      }
      return null;
    }
  }
}
