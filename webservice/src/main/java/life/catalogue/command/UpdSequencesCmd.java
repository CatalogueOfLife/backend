package life.catalogue.command;

import com.google.common.base.Preconditions;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.ImportState;
import life.catalogue.dao.DatasetImportDao;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dao.SectorImportDao;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.*;

import net.sourceforge.argparse4j.inf.Subparser;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates missing project sequences and updates their current value based on existing data.
 * For non project dataset key arguments sequences will be removed if existing.
 */
public class UpdSequencesCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(UpdSequencesCmd.class);
  private static final String ARG_KEY = "key";
  private static final String ARG_ALL = "all";

  public UpdSequencesCmd() {
    super("updSequences", false, "Create and update all sequences for the given projects dataset key");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("--"+ ARG_ALL)
      .dest(ARG_ALL)
      .type(Boolean.class)
      .required(false)
      .setDefault(false)
      .help("Update all projects");
    subparser.addArgument("--"+ ARG_KEY, "-k")
      .dest(ARG_KEY)
      .type(Integer.class)
      .required(false)
      .help("Dataset key for project to update");
  }

  @Override
  public void execute() throws Exception {
    // setup
    if (ns.getBoolean(ARG_ALL)) {
      updateAll();
    } else {
      var key = ns.getInt(ARG_KEY);
      Preconditions.checkArgument(key != null, "Single key parameter required to specify a project to update");
      updateDataset(key);
    }
  }

  private void updateAll() {
    List<Integer> keys;
    LOG.info("Start sequence update for all datasets");
    try (SqlSession session = factory.openSession()) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      keys = dm.keys(DatasetOrigin.PROJECT);
    }
    for (int key : keys) {
      updateDataset(key);
    }
  }

  private void updateDataset(int key) {
    final var info = DatasetInfoCache.CACHE.info(key);

    try (SqlSession session = factory.openSession(true)) {
      var dpm = session.getMapper(DatasetPartitionMapper.class);
      if (info.origin == DatasetOrigin.PROJECT) {
        LOG.info("Create and update sequences for project {}", key);
        // create if not exists
        dpm.createSequences(key);
        dpm.updateSequences(key);

      } else {
        LOG.info("Dataset {} is not a project. Remove sequences if existing", key);
        dpm.deleteSequences(key);
      }
    }
  }
}
