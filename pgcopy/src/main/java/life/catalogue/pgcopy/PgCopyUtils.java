package life.catalogue.pgcopy;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.input.ProxyInputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

public class PgCopyUtils {
  private static final Logger LOG = LoggerFactory.getLogger(PgCopyUtils.class);
  private static final String[] EMPTY_ARRAY = new String[0];
  private final static String CSV = "CSV %s NULL '' ENCODING 'UTF8'";
  private final static String TSV = "NULL '' DELIMITER E'\t' ENCODING 'UTF8'";
  private final static String BINARY = "BINARY ENCODING 'UTF8'";

  public static long loadCSV(PgConnection con, String table, String resourceName) throws IOException, SQLException {
    return loadCSV(con, table, resourceName, Collections.emptyMap(), null);
  }
  
  public static long loadCSV(PgConnection con, String table, String resourceName, Map<String, Object> defaults) throws IOException, SQLException {
    return loadCSV(con, table, resourceName, defaults, null);
  }
  
  public static long loadCSV(PgConnection con, String table, String resourceName,
                             Map<String, Object> defaults,
                             List<CsvFunction> funcs) throws IOException, SQLException {
    return loadCSV(con, table, PgCopyUtils.class.getResourceAsStream(resourceName), defaults, funcs);
  }

  public static long loadTSV(PgConnection con, String table, File csv) throws IOException, SQLException {
    HeadlessStreamTSV in = new HeadlessStreamTSV(new FileInputStream(csv));
    return load(con, table, in.getHeader(), in, TSV);
  }

  /**
   * @param csv input stream of CSV file with header rows being the column names in the postgres table
   */
  public static long loadCSV(PgConnection con, String table, InputStream csv,
                             Map<String, Object> defaults,
                             List<CsvFunction> funcs) throws IOException, SQLException {
    HeadlessStreamCSV in = new HeadlessStreamCSV(csv, defaults, funcs);

    return load(con, table, in.getHeader(), in, String.format(CSV, ""));
  }

  public static long loadBinary(PgConnection con, String table, List<String> columns, File f) throws IOException, SQLException {
    return load(con, table, columns, new FileInputStream(f), BINARY);
  }

  /**
   * @param in input stream of CSV/TSV file with header rows removed and being the column names in the postgres table
   */
  private static long load(PgConnection con, String table, List<String> columns, InputStream in, String with) throws IOException, SQLException {
    con.setAutoCommit(false);
    CopyManager copy = ((PGConnection)con).getCopyAPI();
    con.commit();

    LOG.debug("Copy to table {}", table);
    // use quotes to avoid problems with reserved words, e.g. group
    String header = columns.stream().map(h -> "\"" + h + "\"").collect(Collectors.joining(","));
    long cnt = copy.copyIn("COPY " + table + "(" + header + ") FROM STDOUT WITH "+with, in);

    con.commit();
    LOG.debug("Copied {} records to {}", cnt, table);
    return cnt;
  }
  
  /**
   * @return parses a postgres array given as <pre>{Duméril,Bibron}</pre>
   */
  public static String[] splitPgArray(String x) {
    return x == null ? EMPTY_ARRAY : x.substring(1, x.length()-1).split(",");
  }
  
  public static String buildPgArray(String[] x) {
    if (x == null) {
      return null;
    }
    String values = Arrays.stream(x).collect(Collectors.joining(","));
    return "{" + values + "}";
  }

  interface HeaderStream {
    List<String> getHeader();
  }

  public static List<String> readCsvHeader(InputStream in) {
    var p = newCsvParser(in);
    return new ArrayList<>(List.of(p.parseNext()));
  }

  static CsvParser newCsvParser(InputStream in) {
    CsvParserSettings cfg = new CsvParserSettings();
    cfg.setDelimiterDetectionEnabled(false);
    cfg.setQuoteDetectionEnabled(true);
    cfg.setReadInputOnSeparateThread(false);
    cfg.setSkipEmptyLines(true);
    cfg.setNullValue(null);
    cfg.setMaxColumns(128);
    cfg.setMaxCharsPerColumn(1024 * 128);
    var parser = new CsvParser(cfg);
    parser.beginParsing(in, StandardCharsets.UTF_8);
    return parser;
  }

  static class HeadlessStreamCSV extends InputStream implements HeaderStream {
    private final static char lineend = '\n';
    private final CsvParser parser;
    private final CsvWriter writer;
    private final List<String> header;
    private final String[] defaultValues;
    private final List<CsvFunction> funcs;
    private byte[] bytes;
    private int idx;

    public HeadlessStreamCSV(InputStream in, Map<String, Object> defaults, List<CsvFunction> funcs
    ) throws IOException {
      parser = newCsvParser(in);

      CsvWriterSettings cfg2 = new CsvWriterSettings();
      cfg2.setQuoteEscapingEnabled(true);
      writer = new CsvWriter(cfg2);

      header        = new ArrayList<>(List.of(parser.parseNext()));
      defaultValues = parseDefaults(defaults);
      this.funcs = initFuncs(funcs);
      next();
    }

    private List<CsvFunction> initFuncs(List<CsvFunction> calculators) {
      List<CsvFunction> funcs = new ArrayList<>();
      if (calculators != null) {
        for (var f : calculators) {
          f.init(header);
          header.addAll(f.columns());
          funcs.add(f);
        }
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

    /**
     * Adds default values as new columns (header & data), but only if the column is not already existing
     */
    private String[] parseDefaults(Map<String, Object> defaults) {
      if (defaults==null || defaults.isEmpty()) {
        return null;
      }
      
      List<String> values = new ArrayList<>();
      for (Map.Entry<String, Object> col : defaults.entrySet()) {
        if (header.contains(col.getKey())) {
          LOG.debug("Default column {} already exists. Ignore default {}.", col.getKey(), col.getValue());

        } else {
          header.add(col.getKey());
          Object val = col.getValue();
          if (val == null) {
            // empty string
            values.add(null);
          } else if (val.getClass().isEnum()) {
            values.add(((Enum) val).name());
          } else {
            values.add(val.toString());
          }
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
      if (funcs != null && !funcs.isEmpty()) {
        for (var mf : funcs) {
          String[] calcVals = mf.apply(line).values().toArray(new String[0]);
          line = ArrayUtils.addAll(line, calcVals);
        }
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

    @Override
    public List<String> getHeader() {
      return header;
    }
  }

  static class HeadlessStreamTSV extends ProxyInputStream implements HeaderStream {
    private final List<String> header;
    public HeadlessStreamTSV(InputStream in) throws IOException {
      super(in);
      // read header row
      List<String> cols = new ArrayList<>();
      StringBuilder buffer = new StringBuilder();
      int b;
      while ((b = in.read()) != -1 && b != '\n') {
        if (b == '\t') {
          cols.add(buffer.toString());
          buffer = new StringBuilder();
        } else {
          buffer.append((char)b);
        }
      }
      cols.add(buffer.toString());
      header = List.copyOf(cols);
    }

    @Override
    public List<String> getHeader() {
      return header;
    }
  }

  /**
   * Uses pg copy to write a select statement to a TSV file with headers encoded in UTF8 using an empty string for NULL values
   * @param sql select statement
   * @param out file to write to
   * @return total number of records dumped
   */
  public static long dumpCSV(PgConnection con, String sql, File out) throws IOException, SQLException {
    return dump(con, sql, out, String.format(CSV, "HEADER") );
  }

  /**
   * Uses pg copy to write a select statement to a TSV file without headers encoded in UTF8 using an empty string for NULL values
   * @param sql select statement
   * @param out file to write to
   * @return total number of records dumped
   */
  public static long dumpCSVNoHeader(PgConnection con, String sql, File out) throws IOException, SQLException {
    return dump(con, sql, out, String.format(CSV, ""));
  }

  /**
   * Uses pg copy to write a select statement to a TSV file with headers encoded in UTF8 using an empty string for NULL values
   * @param sql select statement
   * @param out file to write to
   * @return total number of records dumped
   */
  public static long dumpTSV(PgConnection con, String sql, File out) throws IOException, SQLException {
    return dump(con, sql, out, "HEADER " + TSV);
  }
  /**
   * Uses pg copy to write a select statement to a TSV file without headers encoded in UTF8 using an empty string for NULL values
   * @param sql select statement
   * @param out file to write to
   * @return total number of records dumped
   */
  public static long dumpTSVNoHeader(PgConnection con, String sql, File out) throws IOException, SQLException {
    return dump(con, sql, out, TSV);
  }

  public static long dumpBinary(PgConnection con, String sql, File out) throws IOException, SQLException {
    return dump(con, sql, out, BINARY);
  }

  /**
   * Uses pg copy to write a select statement to a UTF8 text file.
   * @param sql select statement
   * @param f file to write to
   * @param with with clause for the copy command. Example: CSV HEADER NULL ''
   * @return total number of records dumped
   */
  public static long dump(PgConnection con, String sql, File f, String with) throws IOException, SQLException {
    con.setAutoCommit(false);
    
    try (OutputStream out = new FileOutputStream(f)) {
      CopyManager copy = con.getCopyAPI();
      var cnt = copy.copyOut("COPY (" + sql + ") TO STDOUT WITH "+with, out);
      return cnt;
    }
  }
}
