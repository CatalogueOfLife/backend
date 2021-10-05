package life.catalogue.dao;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.db.mapper.DatasetPartitionMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.ws.rs.DELETE;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static life.catalogue.common.lang.Exceptions.interruptIfCancelled;

/**
 * Utils class that wraps the main DatasetPartitionMapper.
 * See DatasetPartitionMapper for the main documentation.
 */
public class Partitioner {
  private static final Logger LOG = LoggerFactory.getLogger(Partitioner.class);

  /**
   * @return list of all dataset suffices for which a name data partition exists.
   */
  public static Set<String> partitionSuffices(Connection con, @Nullable DatasetOrigin origin) throws SQLException {
    try (Statement st = con.createStatement();
         Statement originStmt = con.createStatement()
    ) {
      Set<String> suffices = new HashSet<>();
      st.execute("select table_name from information_schema.tables where table_schema='public' and (table_name ~* '^name_\\d+' OR table_name ~* '^name_mod\\d+')");
      ResultSet rs = st.getResultSet();

      Pattern TABLE = Pattern.compile("name_(.+)$");
      while (rs.next()) {
        String tbl = rs.getString(1);
        Matcher m = TABLE.matcher(tbl);
        if (m.find()) {
          if (origin != null) {
            try {
              int key = Integer.parseInt(m.group(1));
              originStmt.execute("select origin from dataset where key = "+key + " AND origin='"+origin.name()+"'::datasetorigin");
              if (!originStmt.getResultSet().next()) {
                // no matching origin
                continue;
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
      rs.close();
      LOG.info("Found {} existing name partition tables", suffices.size());
      return suffices;
    }
  }

  public static synchronized void createDefaultPartitions(SqlSessionFactory factory, int number) {
    try (SqlSession session = factory.openSession(false)) {
      createDefaultPartitions(session, number);
      session.commit();
    }
  }

  /**
   * Creates a given number of partitions for the default, hashed partition
   * including all required sequences.
   * @param number of partitions to create
   */
  public static synchronized void createDefaultPartitions(SqlSession session, int number) {
    LOG.info("Create default partition with {} subpartitions for external datasets", number);
    DatasetPartitionMapper mapper = session.getMapper(DatasetPartitionMapper.class);
    mapper.createDefaultPartitions(number, true);
    session.commit();
  }

  public static synchronized void partition(SqlSessionFactory factory, int datasetKey, DatasetOrigin origin) {
    try (SqlSession session = factory.openSession(false)) {
      partition(session, datasetKey, origin);
      session.commit();
    }
  }

  /**
   * Creates all dataset partitions needed, removing any previous partition and data for the given datasetKey.
   * To avoid table deadlocks we synchronize this method!
   * See https://github.com/Sp2000/colplus-backend/issues/127
   */
  public static synchronized void partition(SqlSession session, int datasetKey, DatasetOrigin origin) {
    interruptIfCancelled();
    LOG.info("Create empty partition for dataset {}", datasetKey);
    DatasetPartitionMapper mapper = session.getMapper(DatasetPartitionMapper.class);
    // first remove if existing
    mapper.delete(datasetKey, origin);
    // then create
    mapper.create(datasetKey, origin);
  }
  
  public static synchronized void delete(SqlSessionFactory factory, int datasetKey, DatasetOrigin origin) {
    try (SqlSession session = factory.openSession(false)) {
      delete(session, datasetKey, origin);
      session.commit();
    }
  }

  /**
   * Deletes an entire partition if its dataset specific or deleted all data from shared partitions, e.g. for external datasets.
   */
  public static synchronized void delete(SqlSession session, int datasetKey, DatasetOrigin origin) {
    interruptIfCancelled();
    LOG.info("Delete partition for dataset {}", datasetKey);
    session.getMapper(DatasetPartitionMapper.class).delete(datasetKey, origin);
  }

  public static synchronized void attach(SqlSessionFactory factory, int datasetKey, DatasetOrigin origin) {
    try (SqlSession session = factory.openSession(true)) {
      // build indices and add dataset bound constraints
      attach(session, datasetKey, origin);
    }
  }

  /**
   * Attaches partitions to main tables thereby building declared indices.
   * To avoid table deadlocks on the main table we synchronize this method.
   * @param session session with auto commit - no transaction allowed here !!!
   */
  public static synchronized void attach(SqlSession session, int datasetKey, DatasetOrigin origin) {
    interruptIfCancelled();
    // attach to main table - this requires an AccessExclusiveLock on all main tables
    // see https://github.com/Sp2000/colplus-backend/issues/387
    LOG.info("Attach partition tables for dataset {}", datasetKey);
    session.getMapper(DatasetPartitionMapper.class).attach(datasetKey, origin);
  }
}
