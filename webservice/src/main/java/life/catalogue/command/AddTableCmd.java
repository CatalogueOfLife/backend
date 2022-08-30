package life.catalogue.command;

import life.catalogue.WsServerConfig;
import life.catalogue.dao.Partitioner;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

/**
 * Command to add new partition tables for a given master table.
 * When adding new partitioned tables to the db schema we need to create a partition table
 * for every existing dataset that has data.
 *
 * The command will look at the existing name partition tables to find the datasets with data.
 * The master table must exist already and be defined to be partitioned by column dataset_key !!!
 */
public class AddTableCmd extends AbstractPromptCmd {
  private static final Logger LOG = LoggerFactory.getLogger(AddTableCmd.class);
  private static final String ARG_TABLE = "table";

  public AddTableCmd() {
    super("addTable", "Adds new partition tables to the database schema");
  }
  
  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    // Adds import options
    subparser.addArgument("--"+ARG_TABLE, "-t")
        .dest(ARG_TABLE)
        .type(String.class)
        .required(true)
        .help("Name of the partitioned table to attach the created partitions to");
  }

  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("Adding partition tables for %s in database %s on %s.", namespace.getString(ARG_TABLE), cfg.db.database, cfg.db.host);
  }

  @Override
  public void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) throws Exception {
    String table = namespace.getString(ARG_TABLE);
    execute(table);
    System.out.println("Done !!!");
  }
  
  private void execute(String table) throws Exception {
    execute(cfg, table);
  }

  static class ForeignKey {
    final String table;
    final String column;
    final boolean cascade;

    public ForeignKey(String table, String column, boolean cascade) {
      this.table = table;
      this.column = column;
      this.cascade = cascade;
    }
  }

  public static void execute(WsServerConfig cfg, String table) throws Exception {
    final String pCreate = "CREATE TABLE %s (LIKE %s INCLUDING DEFAULTS INCLUDING CONSTRAINTS)";
    final String pAttach = "ALTER TABLE %s ATTACH PARTITION %s FOR VALUES IN ( %s )";

    LOG.info("Start adding partition tables for {}", table);
    try (Connection con = cfg.db.connect(cfg.db);
         Statement st = con.createStatement();
         PreparedStatement pExists = con.prepareStatement("SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name = ? ")
    ) {
      for (String suffix : Partitioner.partitionSuffices(con, null)) {
        final String pTable = table+"_"+suffix;
        if (exists(pExists, pTable)) {
          LOG.info("{} already exists", pTable);

        } else {
          LOG.info("Create table {}", pTable);
          st.execute(String.format(pCreate, pTable, table));
          // attach
          st.execute(String.format(pAttach, table, pTable, suffix));
        }
      }
    }
  }

  private static boolean exists(PreparedStatement pExists, String table) throws SQLException {
    pExists.setString(1, table);
    pExists.execute();
    ResultSet rs = pExists.getResultSet();
    boolean exists = rs.next();
    rs.close();
    return exists;
  }

  /**
   * Looks for the known foreign key columns in the master table
   */
  @VisibleForTesting
  protected static List<ForeignKey> analyze(Statement st, String table) throws SQLException {
    List<ForeignKey> fks = new ArrayList<>();

    final List<ForeignKey> knownFks = List.of(
            new ForeignKey("name", "name_id", true),
            new ForeignKey("reference", "reference_id", true),
            new ForeignKey("reference", "published_in_id", true),
            new ForeignKey("verbatim", "verbatim_key", false)
    );

    st.execute("SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = '"+table+"'");
    ResultSet rs = st.getResultSet();
    while (rs.next()) {
      String col = rs.getString(1);
      for (ForeignKey fk : knownFks) {
        if (fk.column.equalsIgnoreCase(col)) {
          fks.add(fk);
          break;
        }
      }
    }
    rs.close();
    return fks;
  }

}
