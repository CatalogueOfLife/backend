package life.catalogue.dao;

import life.catalogue.db.mapper.DatasetPartitionMapper;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
  private static final Pattern TABLE_PATTERN = Pattern.compile("name_mod(\\d+)$");

  /**
   * @return number of partitions existing for the name table
   */
  public static int detectPartitionNumber(Connection con) throws SQLException {
    try (Statement st = con.createStatement()) {
      int max = -1;
      st.execute("select table_name from information_schema.tables where table_schema='public' and (table_name ~* '^name_mod\\d+')");

      int counter = 0;
      try (ResultSet rs = st.getResultSet()) {
        while (rs.next()) {
          String tbl = rs.getString(1);
          Matcher m = TABLE_PATTERN.matcher(tbl);
          if (m.find()) {
            counter++;
            int n = Integer.parseInt(m.group(1));
            if (n > max) {
              max = n;
            }
          } else {
            throw new IllegalStateException("Invalid table name found: "+tbl);
          }
        }
      }

      if (counter != max+1) {
        LOG.warn("Found {} name partition tables, but a maximum suffix of {}", counter, max);
      }
      LOG.info("Found {} existing name partition tables", max+1);
      return max+1;
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

  public static synchronized void createPartitions(SqlSessionFactory factory, String table, int number) {
    try (SqlSession session = factory.openSession(false)) {
      LOG.info("Create {} partitions for table {}", number, table);
      DatasetPartitionMapper mapper = session.getMapper(DatasetPartitionMapper.class);
      mapper.createPartitions(table, number);
      session.commit();
    }
  }

  /**
   * Creates a given number of hashed partitions for all partitioned tables
   * and also sets up all required sequences.
   * @param number of partitions to create
   */
  public static synchronized void createPartitions(SqlSession session, int number) {
    LOG.info("Create {} partitions for all partitioned tables", number);
    DatasetPartitionMapper mapper = session.getMapper(DatasetPartitionMapper.class);
    mapper.createPartitions(number);
    session.commit();
  }

}
