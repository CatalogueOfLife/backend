package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.SimpleName;
import life.catalogue.common.io.TempFile;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.config.MatchingConfig;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.TaxonMapper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Matching job for users that does not the power to insert names into the names index.
 * Rematching of datasets is done by the DatasetMatcher instead.
 */
public class MatchingJob extends AbstractMatchingJob {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingJob.class);
  private final SqlSessionFactory factory;

  public MatchingJob(MatchingRequest req, int userKey, SqlSessionFactory factory, UsageMatcherFactory matcherFactory, MatchingConfig cfg) throws IOException {
    super(req, userKey, loadDataset(factory, req.getDatasetKey()),
      loadRootClassification(req.getTaxonDSID(), factory),
      matcherFactory.persistent(req.getDatasetKey()),
      cfg, matcherFactory.getNameIndex()
    );
    this.factory = factory;
  }

  private static List<SimpleName> loadRootClassification(DSID<String> key, SqlSessionFactory factory) {
    if (key != null) {
      try (SqlSession session = factory.openSession(true)) {
        var rootClassification = session.getMapper(TaxonMapper.class).classificationSimple(key);
        if (rootClassification == null || rootClassification.isEmpty()) {
          // make sure root does exist
          SimpleName root = session.getMapper(NameUsageMapper.class).getSimple(key);
          if (root == null) {
            throw new NotFoundException("Root taxon " + key.getId() + " does not exist in dataset " + key.getDatasetKey());
          }
        }
        return rootClassification;
      }
    }
    return List.of();
  }

  @Override
  public final void runWithLock() throws Exception {
    File resultFile = cfg.randomUploadFile(".zip");
    try (TempFile tmp = TempFile.created(resultFile)) {
      LOG.info("Write matches for job {} to temp file {}", getKey(), tmp.file.getAbsolutePath());
      try (var fos = new FileOutputStream(tmp.file)) {
        matchToOut(fos);
      }
      // move to final result file
      FileUtils.copyFile(tmp.file, result.getFile());
      result.calculateSizeAndMd5();
      LOG.info("Matching {} with {} usages to dataset {} completed: {} [{}]", getKey(), counter.size(), datasetKey, result.getFile(), result.getSizeWithUnit());
    }
  }

  @Override
  public SqlSession openSession() {
    return factory.openSession();
  }

}
