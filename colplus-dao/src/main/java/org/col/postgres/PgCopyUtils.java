package org.col.postgres;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.common.base.Joiner;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.postgresql.jdbc.PgConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PgCopyUtils {
  private static final Logger LOG = LoggerFactory.getLogger(PgCopyUtils.class);
  private static final Joiner HEADER_JOINER = Joiner.on(",");
  
  
  public static long copy(PgConnection con, String table, String resourceName) throws IOException, SQLException {
    return copy(con, table, resourceName, Collections.emptyMap());
  }
  
  public static long copy(PgConnection con, String table, String resourceName, String nullValue) throws IOException, SQLException {
    return copy(con, table, resourceName, Collections.emptyMap(), nullValue);
  }

  public static long copy(PgConnection con, String table, String resourceName, Map<String, Object> defaults) throws IOException, SQLException {
    return copy(con, table, resourceName, defaults, "");
  }
  
  public static long copy(PgConnection con, String table, String resourceName, Map<String, Object> defaults, String nullValue) throws IOException, SQLException {
    con.setAutoCommit(false);
    CopyManager copy = ((PGConnection)con).getCopyAPI();
    con.commit();
  
    LOG.info("Copy {} to table {}", resourceName, table);
    InputStreamWithoutHeader in = new InputStreamWithoutHeader(PgCopyUtils.class.getResourceAsStream(resourceName), ',', '\n', defaults);
    String header = HEADER_JOINER.join(in.header);
    long cnt = copy.copyIn("COPY " + table + "(" + header + ") FROM STDOUT WITH CSV NULL '"+nullValue+"'", in);

    con.commit();
    return cnt;
  }
  
  static class InputStreamWithoutHeader extends InputStream {
    
    private final char delimiter;
    private final char lineend;
    private final InputStream stream;
    private final List<String> header = Lists.newArrayList();
    private final String defaultValues;
    private int defaultValueIdx = -1;
  
    public InputStreamWithoutHeader(InputStream stream, char delimiter, char lineEnding, Map<String, Object> defaults) {
      this.delimiter = delimiter;
      this.lineend = lineEnding;
      this.stream = stream;
      readHeader();
      defaultValues = parseDefaults(defaults);
    }
    
    private void readHeader() {
      try {
        int x = stream.read();
        StringBuffer sb = new StringBuffer();
        while (x >= 0) {
          char c = (char) x;
          if (c == delimiter) {
            header.add(sb.toString());
            sb = new StringBuffer();
          } else if (c == lineend) {
            header.add(sb.toString());
            break;
          } else {
            sb.append(c);
          }
          x = stream.read();
        }
      } catch (IOException e) {
        Throwables.propagate(e);
      }
    }
    
    private String parseDefaults(Map<String, Object> defaults) {
      if (defaults==null || defaults.isEmpty()) {
        return null;
      }
      StringBuilder sb = new StringBuilder();
      for (Map.Entry<String, Object> col : defaults.entrySet()) {
        header.add(col.getKey());
        sb.append(delimiter);
        Object val = col.getValue();
        if (val == null) {
          // empty string
        } else if (Integer.class.isAssignableFrom(val.getClass())) {
          sb.append((int)val);
        } else if (Long.class.isAssignableFrom(val.getClass())) {
          sb.append((long)val);
        } else if (Boolean.class.isAssignableFrom(val.getClass())) {
          sb.append((boolean) val);
        } else {
          sb.append(val);
        }
      }
      LOG.debug("Convert defaults {} to value string {}", defaults, sb.toString());
      return sb.toString();
    }
    
    @Override
    public int available() throws IOException {
      return stream.available();
    }
    
    @Override
    public void close() throws IOException {
      stream.close();
    }
    
    @Override
    public void mark(int readlimit) {
      stream.mark(readlimit);
    }
    
    @Override
    public boolean markSupported() {
      return false;
    }
    
    @Override
    public int read() throws IOException {
      if (defaultValues != null && defaultValueIdx == defaultValues.length()) {
        defaultValueIdx = -1;
        return lineend;
      } else if (defaultValueIdx > 0) {
        return defaultValues.charAt(defaultValueIdx++);
      }
      int x = stream.read();
      // add default values after end of line
      if (x == lineend && defaultValues != null) {
        defaultValueIdx = 1;
        return defaultValues.charAt(0);
      }
      return x;
    }
    
    @Override
    public int read(byte[] b) throws IOException {
      return super.read(b);
    }
    
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return super.read(b, off, len);
    }
  
    @Override
    public void reset() throws IOException {
      stream.reset();
    }
    
    @Override
    public long skip(long n) throws IOException {
      return stream.skip(n);
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
