package life.catalogue.db.tree;

import life.catalogue.api.exception.TooManyRequestsException;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.common.io.UnixCmdUtils;
import life.catalogue.dao.DatasetDao;
import life.catalogue.dao.FileMetricsDao;
import life.catalogue.dao.FileMetricsDatasetDao;
import life.catalogue.db.mapper.DatasetImportMapper;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import life.catalogue.db.mapper.NameUsageMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import com.google.common.collect.Lists;

import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasetDiffService extends BaseDiffService<Integer> {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetDiffService.class);

  private final Set<Integer> userDiffs = ConcurrentHashMap.newKeySet();

  public DatasetDiffService(SqlSessionFactory factory, FileMetricsDatasetDao dao, int timeoutInSeconds) {
    super(dao, factory, timeoutInSeconds);
  }

  @Override
  int[] parseAttempts(Integer datasetKey, String attempts) {
    return parseAttempts(attempts, new Supplier<List<? extends ImportAttempt>>() {
      @Override
      public List<? extends ImportAttempt> get() {
        try (SqlSession session = factory.openSession(true)) {
          return session.getMapper(DatasetImportMapper.class)
              .list(datasetKey, Lists.newArrayList(ImportState.FINISHED), new Page(0, 2));
        }
      }
    });
  }

  /**
   * Generates a names diff between the current version of any two datasets and optional roots to restrict to.
   */
  public Reader datasetNamesDiff(int userKey, int key, List<String> root, int key2, List<String> root2, Rank lowestRank, boolean inclSynonyms) throws IOException {
    if (key == key2) {
      throw new IllegalArgumentException("Diffs need to be between different datasets");
    }
    if (userDiffs.contains(userKey)) {
      throw new TooManyRequestsException("Diffs need to be between different datasets");
    }

    try {
      userDiffs.add(userKey); // lock, we only allow a single diff per user
      DatasetDao ddao = new DatasetDao(factory, null, null, null, null, null, null, null, null);
      Dataset d1 = ddao.getOr404(key);
      Dataset d2 = ddao.getOr404(key2);

      return udiff(
        sortedNamesFile(userKey, d1, root, lowestRank, inclSynonyms), label(key),
        sortedNamesFile(userKey, d2, root2, lowestRank, inclSynonyms), label(key2),
        0, false);

    } finally {
      userDiffs.remove(userKey); // unlock
    }
  }

  private File sortedNamesFile(int userKey, Dataset d, List<String> root, Rank lowestRank, boolean inclSynonyms) throws IOException {
    List<SimpleName> names = new ArrayList<>();
    try (SqlSession session = factory.openSession(true)) {
      var num = session.getMapper(NameUsageMapper.class);
      if (root != null) {
        for (String taxonID : root) {
          var u = num.getSimple(DSID.of(d.getKey(), taxonID));
          if (u == null) throw new IllegalArgumentException("Taxon " + taxonID + " not existing in dataset " + d.getKey());
          names.add(u);
        }
      }

      File f = File.createTempFile("coldiff-src"+d.getKey()+"-", ".txt");
      f.deleteOnExit();

      try (FileMetricsDao.NamesWriter handler = new FileMetricsDao.NamesWriter(f, false)) {
        if (names.isEmpty()) {
          num.processTreeSimple(d.getKey(), null, null, null, lowestRank, inclSynonyms)
             .forEach(sn -> handler.accept(sn.getLabel()));
          LOG.info("Written {} name usages to diff file {} from dataset {}: {}", handler.counter, f, d.getKey(), d.getAliasOrTitle());

        } else {
          for (SimpleName start : names) {
            num.processTreeSimple(d.getKey(), null, start.getId(), null, lowestRank, inclSynonyms)
               .forEach(sn -> handler.accept(sn.getLabel()));
            LOG.info("Written {} name usages to diff file {} for root {} from dataset {}: {}", handler.counter, f, start.getLabel(), d.getKey(), d.getAliasOrTitle());
          }
        }
      }

      // sort file!
      UnixCmdUtils.sort(f);

      return f;
    }
  }

}
