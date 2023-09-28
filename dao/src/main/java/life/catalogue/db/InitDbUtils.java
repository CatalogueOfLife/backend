package life.catalogue.db;

import life.catalogue.api.vocab.Users;
import life.catalogue.postgres.PgCopyUtils;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableMap;
import com.zaxxer.hikari.pool.HikariProxyConnection;

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

}
