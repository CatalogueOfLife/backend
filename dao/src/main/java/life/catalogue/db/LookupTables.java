package life.catalogue.db;

import life.catalogue.api.vocab.Country;
import life.catalogue.api.vocab.Language;
import life.catalogue.common.func.Predicates;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.sql.*;
import java.util.Arrays;

import org.postgresql.util.PSQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LookupTables {

  private static final Logger LOG = LoggerFactory.getLogger(LookupTables.class);

  /**
   * Creates lookup table for java enumerations in the database: __ranks, __country and __language
   * Running the method will drop all existing tables first.
   */
  public static void recreateTables(Connection c) throws SQLException, IOException {
    LOG.info("Check existing lookup tables");
    c.setAutoCommit(true);
    if (count(c, "__ranks") != Arrays.stream(Rank.values()).filter(Predicates.not(Rank::isUncomparable)).count()) {
      ranks(c);
    }
    if (count(c, "__country") != Country.values().length) {
      countries(c);
    }
    if (count(c, "__language") != Language.values().size()) {
      languages(c);
    }
  }

  private static int count(Connection c, String table) throws SQLException, IOException {
    try (Statement st = c.createStatement()) {
      st.execute("SELECT count(*) FROM " + table);
      ResultSet rs = st.getResultSet();
      rs.next();
      int cnt = rs.getInt(1);
      rs.close();
      return cnt;

    } catch (PSQLException e) {
      return -1;
    }
  }

  private static void ranks(Connection c) throws SQLException, IOException {
    try (Statement st = c.createStatement();
         PreparedStatement pst = c.prepareStatement("INSERT INTO __ranks (key, marker) values (?::rank, ?)")
    ) {
      LOG.info("Recreate lookup table for ranks");
      st.execute("DROP TABLE IF EXISTS __ranks");
      st.execute("CREATE TABLE __ranks (key rank PRIMARY KEY, marker TEXT)");
      for (Rank r : Rank.values()) {
        // exclude infrasp., see https://github.com/Sp2000/colplus-backend/issues/478
        if (r.isUncomparable()) continue;
        pst.setString(1, r.name());
        pst.setString(2, r.getMarker());
        pst.execute();
      }
    }
  }

  private static void countries(Connection c) throws SQLException, IOException {
    try (Statement st = c.createStatement();
         PreparedStatement pst = c.prepareStatement("INSERT INTO __country (code, title) values (?, ?)")
    ) {
      LOG.info("Recreate lookup table for countries");
      st.execute("DROP TABLE IF EXISTS __country");
      st.execute("CREATE TABLE __country (code text PRIMARY KEY, title TEXT)");
      for (Country cn : Country.values()) {
        pst.setString(1, cn.getIso2LetterCode());
        pst.setString(2, cn.getTitle());
        pst.execute();
      }
    }
  }

  private static void languages(Connection c) throws SQLException, IOException {
    try (Statement st = c.createStatement();
         PreparedStatement pst = c.prepareStatement("INSERT INTO __language (code, title) values (?, ?)")
    ) {
      LOG.info("Recreate lookup table for languages");
      st.execute("DROP TABLE IF EXISTS __language");
      st.execute("CREATE TABLE __language (code text PRIMARY KEY, title TEXT)");
      for (Language l : Language.values()) {
        pst.setString(1, l.getCode());
        pst.setString(2, l.getTitle());
        pst.execute();
      }
    }
  }
}
