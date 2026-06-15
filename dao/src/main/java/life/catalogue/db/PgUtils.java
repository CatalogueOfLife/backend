package life.catalogue.db;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.postgresql.PGConnection;
import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PgUtils {
  private static final Logger LOG = LoggerFactory.getLogger(PgUtils.class);
  private static final char ZERO_BYTE_CHAR = 0x00;
  public static final String CODE_UNIQUE = "23505";
  public static final String CODE_EXCLUSION = "23P01";

  private static final Pattern UNIQUE = Pattern.compile("unique constraint \"([a-z]+)_");
  private static final Pattern UNIQUE_DETAILS = Pattern.compile("Detail: Key \\(([a-z_-]+)\\)=\\((.*)\\) already exists");
  private static final Pattern EXCLUSION = Pattern.compile("exclusion constraint \"([a-z]+)_");
  private static final Pattern EXCLUSION_DETAILS = Pattern.compile("Detail: Key \\(([a-z_-]+)\\)=\\((.*)\\) conflicts with existing key");

  private PgUtils () {

  }

  /**
   * Builds a short, human readable message for a unique or exclusion constraint violation,
   * e.g. {@code Decision with dataset_key, subject_dataset_key, subject_id='3, 11, foo' already exists}.
   * Falls back to the underlying postgres message if the exception is not a known constraint violation
   * or cannot be parsed.
   */
  public static String constraintMessage(PersistenceException e) {
    if (e.getCause() instanceof PSQLException pe) {
      String state = pe.getSQLState();
      if (CODE_UNIQUE.equals(state)) {
        return constraintMessage(pe, UNIQUE, UNIQUE_DETAILS);
      } else if (CODE_EXCLUSION.equals(state)) {
        return constraintMessage(pe, EXCLUSION, EXCLUSION_DETAILS);
      }
    }
    return toMessage(e);
  }

  private static String constraintMessage(PSQLException e, Pattern constraint, Pattern details) {
    Matcher m = constraint.matcher(e.getMessage());
    String entity = "Entity";
    if (m.find()) {
      entity = StringUtils.capitalize(m.group(1));
      Matcher dm = details.matcher(e.getMessage());
      if (dm.find()) {
        return entity + " with " + dm.group(1) + "='" + dm.group(2) + "' already exists";
      }
    }
    return entity + " already exists";
  }

  /**
   * Tries to return only the main underlying PSQLException message without its stacktrace
   */
  public static String toMessage(PersistenceException e) {
    if (e.getCause() instanceof PSQLException) {
      return e.getCause().getMessage();
    }
    return e.getMessage();
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
   * Converts a cursor into a list and closes the cursor afterwards.
   * @param cursor
   * @return
   * @param <T>
   */
  public static <T> List<T> toList(Cursor<T> cursor) {
    var list = new ArrayList<T>();
    cursor.forEach(list::add);
    try {
      cursor.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return list;
  }

  /**
   * Removes the zero byte character from strings which is not allowed in postgres UTF8:
   * https://stackoverflow.com/questions/70494658/org-postgresql-util-psqlexception-error-invalid-byte-sequence-for-encoding-ut
   */
  public static String repl0x(String x) {
    return x == null ? null : x.replace(ZERO_BYTE_CHAR, ' ');
  }

  public static void killNoneIdleConnections(SqlSessionFactory factory) throws SQLException {
    try (var session = factory.openSession(true)) {
      killNoneIdleConnections(session);
    }
  }

  public static void killNoneIdleConnections(SqlSession session) throws SQLException {
    killNoneIdleConnections(session.getConnection());
  }

  public static void killNoneIdleConnections(Connection c) throws SQLException {
    try (var st = c.createStatement()) {
      PGConnection pgc = InitDbUtils.toPgConnection(c);
      LOG.warn("Kill all open connections but {}", pgc.getBackendPID());
      st.execute("SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE pid != pg_backend_pid() AND datname IS NOT NULL AND leader_pid IS NULL AND state <> 'idle'");
    }
  }

}
