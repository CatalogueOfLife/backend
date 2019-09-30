package org.col.postgres;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;
import org.apache.commons.lang3.ArrayUtils;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PgCopyUtils {
  private static final Logger LOG = LoggerFactory.getLogger(PgCopyUtils.class);
  private static final Joiner HEADER_JOINER = Joiner.on(",");
  
  public static long copy(PgConnection con, String table, String resourceName) throws IOException, SQLException {
    return copy(con, table, resourceName, Collections.emptyMap(), null, "");
  }
  
  public static long copy(PgConnection con, String table, String resourceName, Map<String, Object> defaults) throws IOException, SQLException {
    return copy(con, table, resourceName, defaults, null, "");
  }
  
  public static long copy(PgConnection con, String table, String resourceName,
                          Map<String, Object> defaults,
                          Map<String, Function<String[], String>> funcs) throws IOException, SQLException {
    return copy(con, table, resourceName, defaults, funcs, "");
  }

  public static long copy(PgConnection con, String table, String resourceName,
                          Map<String, Object> defaults,
                          Map<String, Function<String[], String>> funcs,
                          String nullValue) throws IOException, SQLException {
    con.setAutoCommit(false);
    CopyManager copy = ((PGConnection)con).getCopyAPI();
    con.commit();
  
    LOG.info("Copy {} to table {}", resourceName, table);
    HeadlessStream in = new HeadlessStream(PgCopyUtils.class.getResourceAsStream(resourceName), defaults, funcs);
    String header = HEADER_JOINER.join(in.header);
    long cnt = copy.copyIn("COPY " + table + "(" + header + ") FROM STDOUT WITH CSV NULL '"+nullValue+"'", in);

    con.commit();
    return cnt;
  }
  
  /**
   * @return parses a postgres array given as <pre>{Duméril,Bibron}</pre>
   */
  public static String[] splitPgArray(String x) {
    return x.substring(1, x.length()-1).split(",");
  }
  
  public static String buildPgArray(String[] x) {
    if (x == null) {
      return null;
    }
    return "{" + HEADER_JOINER.join(x) + "}";
  }

  static class HeadlessStream extends InputStream {
    private final static char lineend = '\n';
    private final CsvParser parser;
    private final CsvWriter writer;
    private final List<String> header;
    private final String[] defaultValues;
    private final List<Function<String[], String>> funcs;
    private byte[] bytes;
    private int idx;

    public HeadlessStream(InputStream in, Map<String, Object> defaults, Map<String, Function<String[], String>> funcs) throws IOException {
      CsvParserSettings cfg = new CsvParserSettings();
      cfg.setDelimiterDetectionEnabled(false);
      cfg.setQuoteDetectionEnabled(false);
      cfg.setReadInputOnSeparateThread(false);
      cfg.setSkipEmptyLines(true);
      cfg.setNullValue(null);
      cfg.setMaxColumns(128);
      cfg.setMaxCharsPerColumn(1024 * 128);
      parser = new CsvParser(cfg);
      parser.beginParsing(in, StandardCharsets.UTF_8);
      
      CsvWriterSettings cfg2 = new CsvWriterSettings();
      cfg2.setQuoteEscapingEnabled(true);
      writer = new CsvWriter(cfg2);
      
      header        = Lists.newArrayList(parser.parseNext());
      defaultValues = parseDefaults(defaults);
      this.funcs = parseFuncs(funcs);
      next();
    }
  
    private List<Function<String[], String>> parseFuncs(Map<String, Function<String[], String>> calculators) {
      if (calculators == null) {
        return null;
      }
      List<Function<String[], String>> funcs = new ArrayList<>();
      for (Map.Entry<String, Function<String[], String>> f : calculators.entrySet()) {
        header.add(f.getKey());
        funcs.add(f.getValue());
      }
      return funcs;
    }
  
    @Override
    public int read() throws IOException {
      if (bytes != null && idx == bytes.length) {
        next();
        return lineend;
      }
      if (bytes == null) {
        return -1;
      }
      return bytes[idx++];
    }
  
    private String[] parseDefaults(Map<String, Object> defaults) {
      if (defaults==null || defaults.isEmpty()) {
        return null;
      }
      
      List<String> values = new ArrayList<>();
      for (Map.Entry<String, Object> col : defaults.entrySet()) {
        header.add(col.getKey());
        Object val = col.getValue();
        if (val == null) {
          // empty string
          values.add(null);
        } else if (val.getClass().isEnum()) {
          values.add(String.valueOf(((Enum) val).ordinal()));
        } else {
          values.add(val.toString());
        }
      }
      LOG.debug("Convert defaults {} to value columns {}", defaults, values.toString());
      return values.toArray(new String[0]);
    }
  
    private boolean next() throws IOException {
      String[] line = parser.parseNext();
      if (line == null || line.length == 0) {
        bytes = null;
        return false;
      }
      // add defaults
      line = ArrayUtils.addAll(line, defaultValues);
      
      // add calculated values
      if (funcs != null) {
        String[] calcVals = new String[funcs.size()];
        for (int x = 0; x<funcs.size(); x++) {
          calcVals[x] = funcs.get(x).apply(line);
        }
        line = ArrayUtils.addAll(line, calcVals);
      }

      // serialize row as char array
      String x = writer.writeRowToString(line);
      bytes = x.getBytes(StandardCharsets.UTF_8);
      idx = 0;
      return true;
    }
  
    @Override
    public void close() throws IOException {
      parser.stopParsing();
      writer.close();
    }
  }
  
  /**
   * Uses pg copy to write a select statement to a CSV file with headers
   * @param sql select statement
   * @param out file to write to
   */
  public static void dump(PgConnection con, String sql, File out) throws IOException, SQLException {
    dump(con, sql, out, "CSV HEADER NULL ''");
  }
  
  /**
   * Uses pg copy to write a select statement to a text file.
   * @param sql select statement
   * @param out file to write to
   * @param with with clause for the copy command. Example: CSV HEADER NULL ''
   */
  public static void dump(PgConnection con, String sql, File out, String with) throws IOException, SQLException {
    con.setAutoCommit(false);
    
    try (FileWriter writer = new FileWriter(out)) {
      CopyManager copy = con.getCopyAPI();
      copy.copyOut("COPY (" + sql + ") TO STDOUT WITH "+with, writer);
    }
  }
}
