package life.catalogue.db;

import org.apache.ibatis.exceptions.PersistenceException;
import org.checkerframework.checker.index.qual.UpperBoundUnknown;
import org.postgresql.util.PSQLException;

public class PgUtils {
  private PgUtils () {

  }

  public static boolean isUniqueConstraint(PersistenceException e) {
    PSQLException pe = (PSQLException) e.getCause();
    // https://www.postgresql.org/docs/12/errcodes-appendix.html
    // 23505 = unique_violation
    return pe.getSQLState().equals("23505");
  }
}
