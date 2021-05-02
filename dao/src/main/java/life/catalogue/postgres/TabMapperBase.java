package life.catalogue.postgres;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;

/**
 * A writer implementation that consumes a result stream from postgres
 * querying using the postgres jdbc copy command.
 * This is a very fast and direct way to execute select sql statements from postgres.
 * <p>
 * Implement addRow to consume a single result row.
 */
public abstract class TabMapperBase extends Writer implements AutoCloseable {
  private static final Logger LOG = LoggerFactory.getLogger(TabMapperBase.class);

  private final int rowSize;
  private int idx;
  private String[] row;
  private String lastPartialString;

  public TabMapperBase(int rowSize) {
    this.rowSize = rowSize;
    this.row = new String[rowSize];
  }

  protected abstract void addRow(String[] row) throws IOException;

  @Override
  public void close() throws IOException {
    // nothing to do, override as needed
  }

  @Override
  public void flush() throws IOException {
    // nothing to do, override as needed
  }

  @Override
  public void write(char[] cbuf, int off, int len) throws IOException {
    try {
      int b = off, t = off + len;
      int bufStart = b;
      while (b < t) {
        char c = cbuf[b];
        if (c == '\n' || c == '\t') {
          if (lastPartialString != null) {
            row[idx] = lastPartialString + new String(cbuf, bufStart, b - bufStart);
            lastPartialString = null;
          } else {
            row[idx] = new String(cbuf, bufStart, b - bufStart);
            if (row[idx].equals("\\N") || row[idx].equals("")) {
              row[idx] = null;
            }
          }
          bufStart = b + 1;
          idx++;
        }
        if (c == '\n') {
          if (idx > 1) {
            // ignore empty rows
            addRow(this.row);
          }
          idx = 0;
          row = new String[rowSize];
        }
        b++;
      }
      if (bufStart <= t) {
        lastPartialString = String.copyValueOf(cbuf, bufStart, b - bufStart);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}