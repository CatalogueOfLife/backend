package life.catalogue.command;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;

import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import net.sourceforge.argparse4j.inf.Subparser;

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
