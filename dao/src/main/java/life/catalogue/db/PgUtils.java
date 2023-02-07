package life.catalogue.db;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.exceptions.PersistenceException;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PgUtils {
  private static final Logger LOG = LoggerFactory.getLogger(PgUtils.class);
  public static final String CODE_UNIQUE = "23505";

  private PgUtils () {

  }

  public static boolean isUniqueConstraint(PersistenceException e) {
    PSQLException pe = (PSQLException) e.getCause();
    // https://www.postgresql.org/docs/12/errcodes-appendix.html
    // 23505 = unique_violation
    return pe.getSQLState().equals(CODE_UNIQUE);
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

  /**
   * Consumes a new cursor using the supplied handler, always closing the cursor at the end.
   */
  public static <T> void consume(Supplier<Cursor<T>> cursorSupplier, Consumer<T> handler) {
    try (var cursor = cursorSupplier.get()){
      cursor.forEach(handler);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
