package life.catalogue.release;

import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.Language;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class LookupTables {

  /**
   * Creates lookup table for java enumerations in the database: __ranks, __country and __language
   * Running the method will drop all existing tables first.
   */
  public static void recreateTables(Connection c) throws SQLException, IOException {
    c.setAutoCommit(false);
    try (Statement st = c.createStatement()) {
      st.execute("DROP TABLE IF EXISTS __ranks");
      st.execute("DROP TABLE IF EXISTS __country");
      st.execute("DROP TABLE IF EXISTS __language");
      st.execute("CREATE TABLE __ranks (key rank PRIMARY KEY, marker TEXT)");
      st.execute("CREATE TABLE __country (code text PRIMARY KEY, title TEXT)");
      st.execute("CREATE TABLE __language (code text PRIMARY KEY, title TEXT)");
    }
    try (PreparedStatement pstR = c.prepareStatement("INSERT INTO __ranks (key, marker) values (?::rank, ?)");
         PreparedStatement pstC = c.prepareStatement("INSERT INTO __country (code, title) values (?, ?)");
         PreparedStatement pstL = c.prepareStatement("INSERT INTO __language (code, title) values (?, ?)")
    ) {
      for (Rank r : Rank.values()) {
        // exclude infrasp., see https://github.com/Sp2000/colplus-backend/issues/478
        if (r.isUncomparable()) continue;
        pstR.setString(1, r.name());
        pstR.setString(2, r.getMarker());
        pstR.execute();
      }
      for (Country cn : Country.values()) {
        pstC.setString(1, cn.getIso2LetterCode());
        pstC.setString(2, cn.getTitle());
        pstC.execute();

      }
      for (Language l : Language.values()) {
        // exclude infrasp., see https://github.com/Sp2000/colplus-backend/issues/478
        pstL.setString(1, l.getCode());
        pstL.setString(2, l.getTitle());
        pstL.execute();
      }
      c.commit();
    }
  }
}
