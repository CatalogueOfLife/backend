package life.catalogue.command;

import life.catalogue.WsServerConfig;
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

import com.google.common.collect.Lists;

import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

/**
 * Command that rebalances the hashed partitions to a new number of given tables.
 * The command
 *  - detaches the old tables
 *  - renames them by prefixing them with underscore
 *  - creates new tables
 *  - copies data from old tables
 *  - attaches new tables
 *  - removes old tables
 */
public class RepartitionCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(RepartitionCmd.class);
  static final String ARG_NUMBERS = "num";
  private int partitions;

  public RepartitionCmd() {
    super("repartition", false,"Repartition data tables");
  }
  
  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("Repartition the hashed tables to %s partitions in database %s on %s.\n", getPartitionConfig(namespace), cfg.db.database, cfg.db.host);
  }

  static void configurePartitionNumber(Subparser subparser) {
    subparser.addArgument("--"+ ARG_NUMBERS)
             .dest(ARG_NUMBERS)
             .type(Integer.class)
             .required(true)
             .help("Number of partitions to create");
  }

  @Override
  public void configure(Subparser subparser) {
    super.configure(subparser);
    configurePartitionNumber(subparser);
  }

  static int getPartitionConfig(Namespace ns){
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

    // detach existing default partitions
    try (SqlSession session = factory.openSession();
         Connection con = session.getConnection();
         Statement st = con.createStatement();
    ){
      DatasetPartitionMapper dpm = session.getMapper(DatasetPartitionMapper.class);

      LOG.info("Analyze table columns");
      for (String t : Lists.reverse(DatasetPartitionMapper.PARTITIONED_TABLES)) {
        tables.put(t, dpm.columns(t).stream()
                         .map(c -> '"'+c+'"')
                         .collect(Collectors.joining(","))
        );
      }
      final int existingPartitions = Partitioner.detectPartitionNumber(con);
      LOG.info("Detach and rename {} existing partitions for external data", existingPartitions);
      for (String t : Lists.reverse(DatasetPartitionMapper.PARTITIONED_TABLES)) {
        for (int mod=0; mod < existingPartitions; mod++) {
          try {
            final String src = partitionName(t, mod);
            if (Partitioner.isAttached(con, src)) {
              st.execute(String.format("ALTER TABLE %s DETACH PARTITION %s", t, src));
            } else {
              LOG.info("  table {} was not attached", src);
            }
            st.execute(String.format("ALTER TABLE %s RENAME TO _%s", src, src));
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }
      }

      LOG.info("Create {} new partitions", partitions);
      dpm.createPartitions(partitions);
      session.commit();

      LOG.info("Copy data to new partitions");
      // disable triggers, e.g. usage counting
      st.execute("SET session_replication_role = replica");
      for (int mod=0; mod < existingPartitions; mod++) {
        // copy partition data
        for (String t : DatasetPartitionMapper.PARTITIONED_TABLES) {
          try {
            final String src = partitionName(t, mod);
            LOG.info("    copy {}", src);
            String cols = tables.get(t);
            st.execute(String.format("INSERT INTO %s (%s) SELECT %s FROM _%s", t, cols, cols, src));
            con.commit();
          } catch (SQLException e) {
            throw new RuntimeException(e);
          }
        }
        // delete old partitions in reverse order
        for (String t : Lists.reverse(DatasetPartitionMapper.PARTITIONED_TABLES)) {
          final String src = partitionName(t, mod);
          LOG.info("    delete {}", src);
          st.execute(String.format("DROP TABLE _%s", src));
          con.commit();
        }
      }
      con.commit();
    }
  }

  private static String partitionName(String table, int mod) {
    return String.format("%s_mod%s", table, mod);
  }
}
