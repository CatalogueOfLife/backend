package life.catalogue.command;

import life.catalogue.WsServerConfig;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.mapper.DatasetPartitionMapper;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

/**
 * Command that rebalances the hashed default partitions to a new number of given tables.
 * The command
 *  - detaches the old tables
 *  - renames them by prefixing them with underscore
 *  -
 */
public class RepartitionCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(RepartitionCmd.class);
  private static final String ARG_NUMBERS = "num";
  private int partitions;

  public RepartitionCmd() {
    super("repartition", false,"Repartition data tables");
  }
  
  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("Repartition the default, hashed data tables to %s partitions in database %s on %s.\n", getPartitionConfig(namespace), cfg.db.database, cfg.db.host);
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    subparser.addArgument("--"+ ARG_NUMBERS)
             .dest(ARG_NUMBERS)
             .type(Integer.class)
             .required(false)
             .help("Number of partitions to create");
  }

  private int getPartitionConfig(Namespace ns){
    Integer p = ns.getInt(ARG_NUMBERS);
    if (p == null || p < 1) {
      throw new IllegalArgumentException("There needs to be at least one partition");
    }
    return p;
  }

  @Override
  void execute() throws Exception {
    partitions = getPartitionConfig(ns);
    System.out.println(describeCmd(ns, cfg));

    // current suffices for external datasets
    final Set<String> existing = new HashSet<>();
    final Map<String, String> tables = new HashMap<>();
    final boolean createDefault = !defaultPartitionsExists();

    // detach existing default partitions
    try (SqlSession session = factory.openSession();
         Connection con = session.getConnection();
         Statement st = con.createStatement();
    ){
      DatasetPartitionMapper dpm = session.getMapper(DatasetPartitionMapper.class);

      LOG.info("Analyze table columns");
      for (String t : Lists.reverse(DatasetPartitionMapper.TABLES)) {
        tables.put(t, dpm.columns(t).stream()
                         .map(c -> '"'+c+'"')
                         .collect(Collectors.joining(","))
        );
      }
      LOG.info("Detach and rename existing partitions for external data");
      for (String key : Partitioner.partitionSuffices(con, DatasetOrigin.EXTERNAL)) {
        existing.add(key);
        final boolean isDefault = key.startsWith("m");
        for (String t : Lists.reverse(DatasetPartitionMapper.TABLES)) {
          try {
            final String src = String.format("%s_%s", t, key);
            String parentTable = t + (isDefault ? "_default" : "");
            if (Partitioner.isAttached(con, src)) {
              st.execute(String.format("ALTER TABLE %s DETACH PARTITION %s", parentTable, src));
            } else {
              LOG.info("  table " +src+ " was not attached");
            }
            st.execute(String.format("ALTER TABLE %s RENAME TO _%s", src, src));
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }
      }
      if (createDefault) {
        LOG.info("Create new default partitions");
      }
      LOG.info("Create "+partitions+" new default subpartitions");
      dpm.createDefaultPartitions(partitions, createDefault);
      session.commit();

      LOG.info("Copy data to new partitions");
      // disable triggers, e.g. usage counting
      st.execute("SET session_replication_role = replica");

      for (String suffix : existing) {
        LOG.info("  source partition "+suffix);
        for (String t : DatasetPartitionMapper.TABLES) {
          final String src = String.format("%s_%s", t, suffix);
          LOG.info("    copy " + src);
          String cols = tables.get(t);
          st.execute(String.format("INSERT INTO %s (%s) SELECT %s FROM _%s", t, cols, cols, src));
          con.commit();
        }
        for (String t : Lists.reverse(DatasetPartitionMapper.TABLES)) {
          final String src = String.format("%s_%s", t, suffix);
          LOG.info("    delete " + src);
          st.execute(String.format("DROP TABLE _%s", src));
          con.commit();
        }
      }
      con.commit();
    }
  }

  private boolean defaultPartitionsExists(){
    try (Connection c = cfg.db.connect();
         Statement st = c.createStatement()
    ){
      // we do a simple test to check if the default partition already exists
      st.execute("SELECT * FROM name_default LIMIT 1");
      return true;
    } catch (SQLException e) {
    }
    return false;
  }
}
