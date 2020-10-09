package life.catalogue.db.type;

import org.postgresql.jdbc.PgConnection;
import org.postgresql.util.PGobject;

import java.sql.*;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * DDL SQL:
 *
 * CREATE TYPE person AS (given text, family text, email text, orcid text);
 * CREATE TABLE company (key int primary key, contact person);
 */
public class PgObjectTest {

  static AtomicInteger id = new AtomicInteger(0);

  static PGobject buildPgObject(String typeName, Function<String, String> escapeFunc, String... cols) throws SQLException {
    PGobject pgObject = new PGobject();
    pgObject.setType(typeName);
    String value = Arrays.stream(cols)
      .map(escapeFunc)
      .collect(Collectors.joining(","));
    pgObject.setValue("(" + value + ")");
    return pgObject;
  }

  /**
   * Quotes all values to not worry about commas.
   * Escapes quotes by doubling them.
   * NULLs become empty strings.
   */
  static String pgEscape(String x) {
    return x == null ? "" : '"' + x.replaceAll("\"", "\"\"") + '"';
  }

  static String pgEscapeDollar(String x) {
    return x == null ? "" : "\"$$" + x + "\"$$";
  }

  static void testEscapeFunc(PreparedStatement insert, PreparedStatement read, Function<String, String> escapeFunc) throws SQLException {
    PGobject pgo = buildPgObject("person", escapeFunc,"O'Hara", "Œre-Fölíñgé", "Maxi\t<oere@foo.bar>\nhidden", "1234,\"5678\".90/x");
    insert.setInt(1, id.incrementAndGet());
    insert.setObject(2, pgo);
    insert.execute();
    System.out.println("\nInserted "+id);

    read.setInt(1, id.get());
    ResultSet rs = read.executeQuery();
    rs.next();
    printCol(rs, 1);
    printCol(rs, 2);
    printCol(rs, 3);
    printCol(rs, 4);
  }

  static void printCol(ResultSet rs, int i) throws SQLException {
    System.out.println("-----");
    System.out.println(rs.getString(i));
  }

  public static void main(String[] args) throws Exception{
    try (PgConnection c = DriverManager.getConnection("jdbc:postgresql://localhost:5432/ctest", "markus", "").unwrap(PgConnection.class)) {
      try (
        PreparedStatement insert = c.prepareStatement("INSERT INTO company (key, contact) VALUES (?,?)");
        PreparedStatement read = c.prepareStatement("SELECT (contact).given, (contact).family, (contact).email, (contact).orcid FROM company WHERE key=?");
        Statement st = c.createStatement()
      ) {
        st.execute("TRUNCATE company");
        testEscapeFunc(insert, read, PgObjectTest::pgEscape);
        testEscapeFunc(insert, read, PgObjectTest::pgEscapeDollar);
      }
    }
  }
}
