package life.catalogue.command;


import life.catalogue.WsServerConfig;
import life.catalogue.db.InitDbUtils;

import net.sourceforge.argparse4j.inf.Namespace;

/**
 * Command that removes and recreates the check constraint for all default partitions
 * using the configured minExternalDatasetKey.
 */
public class UpdDatasetKeyCheckCmd extends AbstractMybatisCmd {

  public UpdDatasetKeyCheckCmd() {
    super("updDatasetKeyCheck", false,"Updating dataset key check constraints for default tables");
  }

  @Override
  void execute() throws Exception {
    InitDbUtils.updateDatasetKeyConstraints(factory, cfg.db.minExternalDatasetKey);
  }

  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("Updating dataset key check constraints for default tables with minExternalDatasetKey=%s in database %s on %s", cfg.db.minExternalDatasetKey, cfg.db.database, cfg.db.host);
  }

}
