package life.catalogue.command.updatedb;

import io.dropwizard.setup.Bootstrap;
import life.catalogue.WsServerConfig;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.command.AbstractPromptCmd;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.PgConfig;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.StringReader;
import java.sql.*;

/**
 * Command to execute given SQL statements for each dataset partition.
 * The command executes a SQL template passed into the command for each dataset where data partitions exist.
 * Before execution of the SQL the command replaces all {KEY} variables in the template with the actual integer key.
 *
 * If the optional "managed" argument is given with any non null value, only managed datasets with a partition are processed.
 *
 * The command will look at the existing name partition tables to find the datasets with data.
 */
public class ExecSqlCmd extends AbstractPromptCmd {
  private static final Logger LOG = LoggerFactory.getLogger(ExecSqlCmd.class);
  private static final String ARG_SQL = "sql";
  private static final String ARG_FILE = "sqlfile";
  private static final String ARG_MANAGED = "managed"; // if non null will process only managed datasets!!!

  public ExecSqlCmd() {
    super("execSql", "Executes a SQL template for each data partition");
  }
  
  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("--"+ ARG_SQL, "-s")
        .dest(ARG_SQL)
        .type(String.class)
        .required(false)
        .help("SQL to execute for each dataset partition");
    subparser.addArgument("--"+ ARG_FILE, "-f")
      .dest(ARG_FILE)
      .type(String.class)
      .required(false)
      .help("File that contains SQL in plain text UTF8 to be executed per dataset partition");
    subparser.addArgument("--"+ ARG_MANAGED, "-f")
      .dest(ARG_MANAGED)
      .type(Boolean.class)
      .required(false)
      .setDefault(false)
      .help("If true restrict only to managed dataset partitions");
  }

  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("Executing SQL per partition table in database %s on %s.\n", cfg.db.database, cfg.db.host);
  }

  @Override
  public void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    String sql;
    String fn = namespace.getString(ARG_FILE);
    if (fn != null) {
      File f = new File(fn);
      if (!f.exists()){
        throw new IllegalArgumentException("File " + f.getAbsolutePath() + " does not exist");
      }
      sql = UTF8IoUtils.readString(f);
    } else {
      sql = namespace.get(ARG_SQL);
    }
    boolean managed = namespace.getBoolean(ARG_MANAGED);
    if (StringUtils.isBlank(sql)) {
      throw new IllegalArgumentException("No sql found to execute");
    }
    execute(sql, managed);
  }

  private void execute(final String template, boolean managedOnly) throws Exception {
    try (Connection con = cfg.db.connect(cfg.db)) {
      ScriptRunner runner = PgConfig.scriptRunner(con);
      for (int key : AddTableCmd.datasetKeys(con)) {
        // only managed datasets?
        if (!managedOnly || DatasetInfoCache.CACHE.origin(key) == DatasetOrigin.MANAGED) {
          String sql = template.replaceAll("\\{KEY}", String.valueOf(key));
          System.out.println("Execute SQL for dataset key " + key);
          runner.runScript(new StringReader(sql));
          con.commit();
        }
      }
    }
  }

}
