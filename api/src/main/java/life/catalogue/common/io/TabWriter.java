package life.catalogue.common.io;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * Basic TAB delimited file writer that escapes tab and newline characters using a backslash.
 * If desired the number of columns can be verified to be the same for every record.
 */
public class TabWriter implements AutoCloseable, RowWriter {
  
  private static final Pattern escapeChars = Pattern.compile("[\t\n\r]");
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
    String rowString = tabRow(row);
    if (rowString != null) {
      writer.write(rowString);
      records++;
    }
  }
  
  private String tabRow(String[] columns) {
    // escape \t \n \r chars !!!
    boolean empty = true;
    for (int i = 0; i < columns.length; i++) {
      if (columns[i] != null) {
        empty = false;
        columns[i] = life.catalogue.common.text.StringUtils.escapePgCopy(columns[i], true);
      }
    }
    if (empty) {
      // dont create a row at all!
      return null;
    }
    return StringUtils.join(columns, '\t') + "\n";
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
