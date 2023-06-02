package life.catalogue.dao;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetPartitionMapper;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utils class that wraps the main DatasetPartitionMapper.
 * See DatasetPartitionMapper for the main documentation.
 */
public class Partitioner {
  private static final Logger LOG = LoggerFactory.getLogger(Partitioner.class);
  private static final Pattern TABLE_PATTERN = Pattern.compile("name_(.+)$");

  /**
   * @return list of all dataset suffices for which a name data partition exists - no matter if attached or not. Includes both
   *         the default partitions for external datasets and project/release tables.
   */
  public static Set<String> partitionSuffices(Connection con, @Nullable DatasetOrigin origin) throws SQLException {
    try (Statement st = con.createStatement();
         Statement originStmt = con.createStatement()
    ) {
      Set<String> suffices = new HashSet<>();
      st.execute("select table_name from information_schema.tables where table_schema='public' and (table_name ~* '^name_\\d+' OR table_name ~* '^name_mod\\d+')");

      try (ResultSet rs = st.getResultSet()) {
        while (rs.next()) {
          String tbl = rs.getString(1);
          Matcher m = TABLE_PATTERN.matcher(tbl);
          if (m.find()) {
            if (origin != null) {
              try {
                int key = Integer.parseInt(m.group(1));
                originStmt.execute("select origin from dataset where key = "+key + " AND origin='"+origin.name()+"'::datasetorigin");
                try (var ors = originStmt.getResultSet()) {
                  if (!ors.next()) {
                    // no matching origin
                    continue;
                  }
                }
              } catch (NumberFormatException e) {
                if (origin != DatasetOrigin.EXTERNAL) {
                  continue;
                }
              }
            }
            suffices.add( m.group(1) );
          }
        }
      }
      LOG.info("Found {} existing name partition tables", suffices.size());
      return suffices;
    }
  }

  /**
   * @return true if a given table is an attached partition table.
   */
  public static boolean isAttached(Connection con, String table) throws SQLException {
    boolean exists = false;
    try (Statement st = con.createStatement()) {
      st.execute("SELECT EXISTS (SELECT child.relname"
                 + "  FROM pg_inherits JOIN pg_class parent ON pg_inherits.inhparent = parent.oid"
                 + "  JOIN pg_class child ON pg_inherits.inhrelid   = child.oid"
                 + "  WHERE child.relname='" + table + "')");
      try (ResultSet rs = st.getResultSet()) {
        if (rs.next()) {
          exists = rs.getBoolean(1);
        }
      }
    }
    return exists;
  }

  public static synchronized void createPartitions(SqlSessionFactory factory, int number) {
    try (SqlSession session = factory.openSession(false)) {
      createPartitions(session, number);
      session.commit();
    }
  }

  /**
   * Creates a given number of partitions for the default, hashed partition
   * including all required sequences.
   * @param number of partitions to create
   */
  public static synchronized void createPartitions(SqlSession session, int number) {
    LOG.info("Create default partition with {} subpartitions for external datasets", number);
    DatasetPartitionMapper mapper = session.getMapper(DatasetPartitionMapper.class);
    mapper.createPartitions(number);
    session.commit();
  }

}
