package life.catalogue.printer;

import life.catalogue.api.exception.TooManyRequestsException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.search.JobSearchRequest;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.config.DiffConfig;
import life.catalogue.dao.EntityDao;
import life.catalogue.dao.FileMetricsDatasetDao;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.printer.diff.DiffInput;
import life.catalogue.printer.diff.DiffNamesParam;

import org.gbif.nameparser.api.Rank;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.annotation.Nullable;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasetDiffService extends BaseDiffService<Integer> {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetDiffService.class);

  private final EntityDao<Integer, Dataset, DatasetMapper> ddao;
  private final Set<Integer> userDiffs = ConcurrentHashMap.newKeySet();

  public DatasetDiffService(SqlSessionFactory factory, FileMetricsDatasetDao dao, DiffConfig diffCfg) {
    super(dao, factory, diffCfg);
    ddao = new EntityDao<>(false, factory, Dataset.class, DatasetMapper.class, null);
  }

  @Override
  int[] parseAttempts(Integer datasetKey, String attempts) {
    final JobSearchRequest req = new JobSearchRequest();
    req.setDatasetKey(datasetKey);
    req.setStates(Set.of(ImportState.FINISHED));
    return parseAttempts(attempts, () -> {
      try (SqlSession session = factory.openSession(true)) {
        return session.getMapper(DatasetImportMapper.class).list(req, new Page(0, 2));
      }
    });
  }

  /** Names diff between the current version of any two datasets, with optional roots/filters. */
  public NamesDiff datasetNamesDiff(int userKey, int key1, List<String> root1, int key2, List<String> root2,
                                    @Nullable Rank lowestRank, boolean inclAuthorship, boolean inclSynonyms, boolean showParent,
                                    @Nullable Rank parentRank, @Nullable Set<Rank> rankFilter) {
    if (key1 == key2) {
      throw new IllegalArgumentException("Diffs need to be between different datasets");
    }
    if (userDiffs.contains(userKey)) {
      throw new TooManyRequestsException("You can only run one diff at a time");
    }
    ddao.getOr404(key1);
    ddao.getOr404(key2);

    LOG.info("Start dataset diff between {} <-> {} by {}", key1, key2, userKey);
    try {
      userDiffs.add(userKey);
      DiffInput a = new DiffInput(label(key1),
        () -> namesStream(param(key1, root1, lowestRank, inclAuthorship, inclSynonyms, showParent, parentRank, rankFilter)));
      DiffInput b = new DiffInput(label(key2),
        () -> namesStream(param(key2, root2, lowestRank, inclAuthorship, inclSynonyms, showParent, parentRank, rankFilter)));
      return engine.diff(a, b, diffOptions());
    } finally {
      userDiffs.remove(userKey);
    }
  }

  private static DiffNamesParam param(int key, @Nullable List<String> roots, @Nullable Rank lowestRank,
                                      boolean authorship, boolean synonyms, boolean showParent,
                                      @Nullable Rank parentRank, @Nullable Set<Rank> rankFilter) {
    DiffNamesParam p = new DiffNamesParam();
    p.setDatasetKey(key);
    if (roots != null && !roots.isEmpty()) p.setRoots(new HashSet<>(roots));
    p.setLowestRank(lowestRank);
    p.setAuthorship(authorship);
    p.setSynonyms(synonyms);
    p.setParentName(showParent);
    p.setParentRank(parentRank);
    p.setRankFilter(rankFilter);
    return p;
  }

  /**
   * Opens a session + forward-only cursor and returns a Stream whose onClose closes both. The engine
   * closes the stream (try-with-resources), releasing cursor and session. Uses openSession(false) so
   * Postgres streams the sort result instead of buffering all rows.
   */
  private Stream<String> namesStream(DiffNamesParam param) {
    SqlSession session = factory.openSession(false);
    try {
      Cursor<String> cursor = session.getMapper(NameUsageMapper.class).processDiffNames(param);
      return StreamSupport.stream(cursor.spliterator(), false)
        .onClose(() -> {
          try { cursor.close(); } catch (Exception e) { LOG.warn("Failed to close diff cursor", e); }
          session.close();
        });
    } catch (RuntimeException e) {
      session.close();
      throw e;
    }
  }
}
