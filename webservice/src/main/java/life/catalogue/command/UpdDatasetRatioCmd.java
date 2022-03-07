package life.catalogue.command;

import com.google.common.annotations.VisibleForTesting;

import io.dropwizard.setup.Bootstrap;

import life.catalogue.WsServerConfig;
import life.catalogue.dao.Partitioner;

import life.catalogue.db.mapper.DatasetMapper;

import life.catalogue.db.mapper.DatasetPartitionMapper;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Command that removed and recreates the check constraint for all default partitions
 * using the configured dataset ratio between projects and external datasets.
 */
public class UpdDatasetRatioCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(UpdDatasetRatioCmd.class);
  private static final String ARG = "ratio";

  public UpdDatasetRatioCmd() {
    super("datasetRatio", false,"Updating dataset key check constraints for default tables");
  }
  
  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds import options
    subparser.addArgument("--" + ARG)
        .dest(ARG)
        .type(Integer.class)
        .setDefault(100)
        .required(true)
        .help("Ratio of number of managed & released datasets and external datasets.");
  }

  @Override
  void execute() throws Exception {
    Integer ratio = ns.getInt(ARG);
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      int max = dm.getMaxKey(1, true);
      session.getMapper(DatasetPartitionMapper.class).updateDatasetKeyChecks(max, ratio);
    }
  }

  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("Updating dataset key check constraints for default tables in database %s on %s.", cfg.db.database, cfg.db.host);
  }

}
