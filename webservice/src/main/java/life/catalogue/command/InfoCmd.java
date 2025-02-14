package life.catalogue.command;

import life.catalogue.WsServerConfig;

import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;

public class InfoCmd extends AbstractPromptCmd {

  public InfoCmd() {
    super("info", "SHow current version and config infos");
  }

  @Override
  public void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    System.out.println("ChecklistBank");
    System.out.println("version: " + cfg.versionString());
    System.out.println("    db : " + (cfg.db == null ? "NULL" : cfg.db.toString()));
    System.out.println("    es : " + (cfg.es == null ? "NULL" : cfg.es.toString()));
    System.out.println("   doi : " + (cfg.doi == null ? "NULL" : cfg.doi.toString()));
    System.out.println("  gbif : " + (cfg.gbif == null ? "NULL" : cfg.gbif.toString()));
  }
}
