package life.catalogue.command;

import life.catalogue.WsServerConfig;
import life.catalogue.dao.Partitioner;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;

import io.dropwizard.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import javax.annotation.Nullable;

/**
 * Command to add new partition tables for a given, partitioned master table.
 *
 * The master table must exist already and be defined to be partitioned by column dataset_key !!!
 */
public class PartitionCmd extends AbstractMybatisCmd {
  private static final Logger LOG = LoggerFactory.getLogger(PartitionCmd.class);
  private static final String ARG_TABLE = "table";

  public PartitionCmd() {
    super("partition", "Adds missing hash partitions for a partitioned table");
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
    subparser.addArgument("--"+ RepartitionCmd.ARG_NUMBERS)
         .dest(RepartitionCmd.ARG_NUMBERS)
         .type(Integer.class)
         .required(true)
         .help("Number of partitions to create");
  }

  @Override
  public String describeCmd(Namespace namespace, WsServerConfig cfg) {
    return String.format("Adding missing partition tables for %s in database %s on %s.", namespace.getString(ARG_TABLE), cfg.db.database, cfg.db.host);
  }

  @Override
  void execute() throws Exception {
    String table = ns.getString(ARG_TABLE);
    Integer num = ns.getInt(RepartitionCmd.ARG_NUMBERS);
    LOG.info("Start adding partition tables for {}", table);
    Partitioner.createPartitions(factory, table, num);
    System.out.println("Done !!!");
  }

}