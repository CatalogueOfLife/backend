package life.catalogue.matching;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.SimpleName;
import life.catalogue.common.io.TempFile;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.config.MatchingConfig;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;

/**
 * Matching job for users that does not the power to insert names into the names index.
 * This matching job does not support mapping names from a CLB source.
 */
public class StreamingMatchingJob extends AbstractMatchingJob {
  private final OutputStream out;

  public StreamingMatchingJob(MatchingRequest req, int userKey, Dataset dataset, UsageMatcher matcher, MatchingConfig cfg, OutputStream out) {
    super(req, userKey, dataset, loadRootClassification(req.getTaxonDSID(), matcher), matcher, cfg, matcher.getNameIndex());
    this.out = out;
  }

  private static List<? extends SimpleName> loadRootClassification(DSID<String> key, UsageMatcher matcher) {
    if (key != null) {
      var root = matcher.store().getSNClassified(key.getId());
      if (root == null) {
        throw new NotFoundException("Root taxon " + key.getId() + " does not exist");
      }
      return root.getClassification();
    }
    return new ArrayList<>();
  }

  @Override
  public void runWithLock() throws Exception {
    matchToOut(out);
  }

  @Override
  public SqlSession openSession() {
    throw new IllegalArgumentException("This service does not support matching against a ChecklistBank source dataset.");
  }
}
