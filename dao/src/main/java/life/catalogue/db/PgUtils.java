package life.catalogue.db;

import org.apache.ibatis.exceptions.PersistenceException;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class PgUtils {
  private static final Logger LOG = LoggerFactory.getLogger(PgUtils.class);

  private PgUtils () {

  }

  public static boolean isUniqueConstraint(PersistenceException e) {
    PSQLException pe = (PSQLException) e.getCause();
    // https://www.postgresql.org/docs/12/errcodes-appendix.html
    // 23505 = unique_violation
    return pe.getSQLState().equals("23505");
  }
  
  public static void createDatabase(Connection con, String database, String user) throws SQLException {
    try (Statement st = con.createStatement()) {
      LOG.info("Drop existing database {}", database);
      st.execute("DROP DATABASE IF EXISTS \"" + database + "\"");

      LOG.info("Create new database {}", database);
      st.execute("CREATE DATABASE  \"" + database + "\"" +
                 " WITH ENCODING UTF8 LC_COLLATE 'C' LC_CTYPE 'C' OWNER " + user + " TEMPLATE template0");

      LOG.info("Use UTC timezone for {}", database);
      st.execute("ALTER DATABASE  \"" + database + "\" SET timezone TO 'UTC'");
    }
  }
}
