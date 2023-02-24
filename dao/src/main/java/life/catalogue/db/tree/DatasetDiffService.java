package life.catalogue.db.tree;

import life.catalogue.api.exception.TooManyRequestsException;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.common.io.UnixCmdUtils;
import life.catalogue.dao.EntityDao;
import life.catalogue.dao.FileMetricsDatasetDao;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;

import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class DatasetDiffService extends BaseDiffService<Integer> {
  private static final Logger LOG = LoggerFactory.getLogger(DatasetDiffService.class);

  // we only use ddao.getOr404()
  private final EntityDao<Integer, Dataset, DatasetMapper> ddao;
  private final Set<Integer> userDiffs = ConcurrentHashMap.newKeySet();

  public DatasetDiffService(SqlSessionFactory factory, FileMetricsDatasetDao dao, int timeoutInSeconds) {
    super(dao, factory, timeoutInSeconds);
    ddao = new EntityDao<>(false, factory, Dataset.class, DatasetMapper.class, null);
  }

  @Override
  int[] parseAttempts(Integer datasetKey, String attempts) {
    return parseAttempts(attempts, () -> {
      try (SqlSession session = factory.openSession(true)) {
        return session.getMapper(DatasetImportMapper.class)
            .list(datasetKey, Lists.newArrayList(ImportState.FINISHED), new Page(0, 2));
      }
    });
  }

  /**
   * Generates a names diff between the current version of any two datasets and optional roots to restrict to.
   */
  public Reader datasetNamesDiff(int userKey, int key1, List<String> root1, int key2, List<String> root2,
                                 Rank lowestRank, boolean inclAuthorship, boolean inclSynonyms, boolean showParent, @Nullable Rank parentRank) throws IOException {
    return datasetDiff(userKey, key1, root1, key2, root2, lowestRank, inclAuthorship, inclSynonyms,showParent, parentRank);
  }

  private Reader datasetDiff(int userKey,
                             int key1, List<String> root1,
                             int key2, List<String> root2,
                             @Nullable Rank lowestRank, boolean inclAuthorship, boolean inclSynonyms, boolean showParent, @Nullable Rank parentRank
  ) throws IOException {
    // preconditions
    if (key1 == key2) {
      throw new IllegalArgumentException("Diffs need to be between different datasets");
    }
    if (userDiffs.contains(userKey)) {
      throw new TooManyRequestsException("You can only run one diff at a time");
    }
    // throw a 404 early in case any of the datasets does not exist
    ddao.getOr404(key1);
    ddao.getOr404(key2);

    // allow one concurrent diff per user
    try {
      userDiffs.add(userKey); // lock, we only allow a single diff per user
      File f1 = printAndSort(key1, root1, lowestRank, inclAuthorship, inclSynonyms, showParent, parentRank);
      File f2 = printAndSort(key2, root2, lowestRank, inclAuthorship, inclSynonyms, showParent, parentRank);
      return udiff(f1, label(key1), f2, label(key2), 0, false);

    } finally {
      userDiffs.remove(userKey); // unlock
    }
  }

  private File printAndSort(int key, @Nullable List<String> roots, @Nullable Rank lowestRank, boolean inclAuthorship, boolean inclSynonyms, boolean showParent, @Nullable Rank parentRank) throws IOException {
    File f = createTempFile(key);
    Writer w = UTF8IoUtils.writerFromFile(f);
    // we need to support multiple roots which a TreePrinter does not deal with
    // we will reuse the writer and append multiple trees if needed
    if (roots == null || roots.isEmpty()) {
      appendRoot(w, key, null, lowestRank, inclAuthorship, inclSynonyms, showParent, parentRank);
    } else {
      for (String r : roots) {
        appendRoot(w, key, r, lowestRank, inclAuthorship, inclSynonyms, showParent, parentRank);
      }
    }
    w.close();
    // sort file
    UnixCmdUtils.sort(f);
    return f;
  }

  private void appendRoot(Writer w, int key, String root, Rank lowestRank, boolean inclAuthorship, boolean inclSynonyms, boolean showParent, Rank parentRank) throws IOException {
    TreeTraversalParameter params = TreeTraversalParameter.dataset(key);
    params.setTaxonID(root);
    params.setLowestRank(lowestRank);
    params.setSynonyms(inclSynonyms);
    NameParentPrinter printer = PrinterFactory.dataset(NameParentPrinter.class, params, factory, w);
    try {
      printer.setPrintAuthorship(inclAuthorship);
      if (showParent) {
        printer.setParentName(parentRank);
      }
      printer.print();
      printer.close();
    } finally {
      printer.close();
    }
  }

  private static File createTempFile(int datasetKey) {
    File f;
    try {
      f = File.createTempFile("coldiff-src" + datasetKey + "-", ".txt");
      f.deleteOnExit();
    } catch (IOException e) {
      throw new RuntimeException("Failed to create temp diff file for dataset "+datasetKey, e);
    }
    return f;
  }

}
