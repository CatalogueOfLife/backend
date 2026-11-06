package life.catalogue.common.io;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Basic TAB delimited file writer that escapes tab and newline characters using a backslash.
 * If desired the number of columns can be verified to be the same for every record.
 */
public class TabWriter implements AutoCloseable, RowWriter {

  private boolean verifyColumns = false;
  private int columns = -1;
  private int records = 0;
  private Writer writer;

  public static TabWriter fromStream(OutputStream stream) {
    return new TabWriter(new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8)));
  }
  
  public static TabWriter fromFile(File file) {
    try {
      return fromStream(new FileOutputStream(file));
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }
  
  public TabWriter(Writer writer) {
    this.writer = writer;
  }
  
  /**
   * If set to true the writer verifies the number of written columns to always be the same.
   *
   * @param verifyColumns
   */
  public void setVerifyColumns(boolean verifyColumns) {
    this.verifyColumns = verifyColumns;
  }
  
  public void write(String[] row) throws IOException {
    if (row == null || row.length == 0) {
      return;
    }
    if (verifyColumns) {
      if (columns < 0) {
        columns = row.length;
      } else if (columns != row.length) {
        throw new IllegalArgumentException("Wrong number of rows. Expected " + columns + " but found " + row.length);
      }
    }
    // escape \t \n \r chars !!!
    boolean empty = true;
    for (int i = 0; i < row.length; i++) {
      if (row[i] != null) {
        empty = false;
        row[i] = life.catalogue.common.text.StringUtils.escapePgCopy(row[i], true);
      }
    }
    if (empty) {
      // dont create a row at all!
      return;
    }
    // write the columns straight to the writer, a null column is an empty one
    for (int i = 0; i < row.length; i++) {
      if (i > 0) {
        writer.write('\t');
      }
      if (row[i] != null) {
        writer.write(row[i]);
      }
    }
    writer.write('\n');
    records++;
  }
  
  @Override
  public void close() throws IOException {
    writer.close();
  }
  
  /**
   * @return number of records already written
   */
  public int getRecords() {
    return records;
  }
}
