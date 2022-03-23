package life.catalogue.db;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.dao.Partitioner;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.db.mapper.DatasetPartitionMapper;
import life.catalogue.postgres.PgCopyUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.zaxxer.hikari.pool.HikariProxyConnection;

import static life.catalogue.common.util.PrimitiveUtils.intDefault;

public class InitDbUtils {
  private static final Logger LOG = LoggerFactory.getLogger(InitDbUtils.class);
  public static final String SCHEMA_FILE = "life/catalogue/db/dbschema.sql";
  public static final String DATA_FILE = "life/catalogue/db/data.sql";

  public static PgConnection toPgConnection(Connection c) throws SQLException {
    PgConnection pgc;
    if (c instanceof HikariProxyConnection) {
      HikariProxyConnection hpc = (HikariProxyConnection) c;
      pgc = hpc.unwrap(PgConnection.class);
    } else {
      pgc = (PgConnection) c;
    }
    return pgc;
  }

  public static void insertDatasets(PgConnection pgc, InputStream csv) throws SQLException, IOException {
    LOG.info("Insert known datasets");
    PgCopyUtils.copy(pgc, "dataset", csv, ImmutableMap.<String, Object>builder()
      .put("created_by", Users.DB_INIT)
      .put("modified_by", Users.DB_INIT)
      .build(), null, "");
    try (Statement st = pgc.createStatement()) {
      st.execute("SELECT setval('dataset_key_seq', (SELECT max(key) FROM dataset))");
      pgc.commit();
    }
  }

  public static void updateDatasetKeyConstraints(SqlSessionFactory factory, int minExternalDatasetKey) {
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);

      int next = Math.max(minExternalDatasetKey, intDefault(dm.getMaxKey(null), 1));
      int previous = intDefault(dm.getMaxKey(next), 10);
      LOG.info("Add external dataset key constraints on the default partitions < {} OR > {}", previous, next);
      session.getMapper(DatasetPartitionMapper.class).updateDatasetKeyChecks(previous, next);
    }
  }

  public static void createNonDefaultPartitions(SqlSessionFactory factory) {
    // add project partitions & dataset key constraints
    try (SqlSession session = factory.openSession(true)) {
      DatasetMapper dm = session.getMapper(DatasetMapper.class);
      for (DatasetOrigin o : DatasetOrigin.values()) {
        for (var dk : dm.keys(o)) {
          Partitioner.partition(session, dk, o);
          Partitioner.attach(session, dk, o);
        }
      }
    }
  }

}
