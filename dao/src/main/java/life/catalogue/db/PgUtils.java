package life.catalogue.db;

import life.catalogue.common.text.StringUtils;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PgUtils {
  private static final Logger LOG = LoggerFactory.getLogger(PgUtils.class);
  private static final char ZERO_BYTE_CHAR = 0x00;
  public static final String CODE_UNIQUE = "23505";
  public static final String CODE_EXCLUSION = "23P01";

  private PgUtils () {

  }

  public static void deferConstraints(SqlSession session) {
    deferConstraints(session.getConnection());
  }

  public static void deferConstraints(Connection con) {
    try (Statement st = con.createStatement()) {
      LOG.info("Defer all constraints in this session");
      st.execute("SET CONSTRAINTS ALL DEFERRED");
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public static boolean isUniqueConstraint(PersistenceException e) {
    if (e.getCause() instanceof PSQLException) {
      PSQLException pe = (PSQLException) e.getCause();
      // https://www.postgresql.org/docs/14/errcodes-appendix.html
      // 23505 = unique_violation
      // 23P01 = exclusion_violation
      return pe.getSQLState().equals(CODE_UNIQUE) || pe.getSQLState().equals(CODE_EXCLUSION);
    }
    return false;
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

  /**
   * Removes the zero byte character from strings which is not allowed in postgres UTF8:
   * https://stackoverflow.com/questions/70494658/org-postgresql-util-psqlexception-error-invalid-byte-sequence-for-encoding-ut
   */
  public static String repl0x(String x) {
    return x == null ? null : x.replace(ZERO_BYTE_CHAR, ' ');
  }
}
