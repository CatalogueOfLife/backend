package life.catalogue.command;


import life.catalogue.WsServerConfig;
import life.catalogue.db.InitDbUtils;
import life.catalogue.db.mapper.DatasetMapper;

import life.catalogue.db.mapper.DatasetPartitionMapper;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.common.util.PrimitiveUtils.intDefault;

/**
 * Command that removes and recreates the check constraint for all default partitions
 * using the configured minExternalDatasetKey.
 */
public class UpdDatasetKeyCheckCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(UpdDatasetKeyCheckCmd.class);

  public UpdDatasetKeyCheckCmd() {
    super("updDatasetKeyCheck", false,"Updating dataset key check constraints for default tables");
  }

  @Override
  void execute() throws Exception {
    InitDbUtils.updateDatasetKeyConstraints(factory, cfg.db.minExternalDatasetKey);
  }

  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("Updating dataset key check constraints for default tables in database %s on %s.", cfg.db.database, cfg.db.host);
  }

}
