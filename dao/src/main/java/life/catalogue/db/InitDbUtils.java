package life.catalogue.db;

import life.catalogue.api.vocab.Users;
import life.catalogue.postgres.PgCopyUtils;

import java.io.IOException;
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
  public static final String DATASETS_FILE = "/life/catalogue/db/dataset.csv";

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

  public static void insertDatasets(PgConnection pgc) throws SQLException, IOException {
    LOG.info("Insert known datasets");
    PgCopyUtils.copy(pgc, "dataset", DATASETS_FILE, ImmutableMap.<String, Object>builder()
      .put("created_by", Users.DB_INIT)
      .put("modified_by", Users.DB_INIT)
      .build());
    // the dataset.csv file was generated as a dump from production with psql:
    // \copy (SELECT key,type,gbif_key,gbif_publisher_key,license,issued,confidence,completeness,origin,title,alias,description,version,geographic_scope,taxonomic_scope,url,logo,notes,settings,source_key,contact,creator,editor,publisher,distributor,contributor FROM dataset WHERE not private and deleted is null and origin = 'EXTERNAL' ORDER BY key) to 'dataset.csv' WITH CSV HEADER NULL '' ENCODING 'UTF8'
    try (Statement st = pgc.createStatement()) {
      st.execute("SELECT setval('dataset_key_seq', (SELECT max(key) FROM dataset))");
      pgc.commit();
    }
  }
}
