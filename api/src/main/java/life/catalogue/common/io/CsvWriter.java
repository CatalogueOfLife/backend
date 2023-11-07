package life.catalogue.common.io;

import com.univocity.parsers.csv.CsvWriterSettings;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * CSV delimited file writer that uses univocity under the hood.
 * If desired the number of columns can be verified to be the same for every record.
 */
public class CsvWriter implements AutoCloseable, RowWriter {
  private static final CsvWriterSettings SETTINGS = new CsvWriterSettings();

  private boolean verifyColumns = false;
  private int columns = -1;
  private int records = 0;
  private com.univocity.parsers.csv.CsvWriter writer;

  public static CsvWriter fromStream(OutputStream stream) {
    return new CsvWriter(new BufferedWriter(new OutputStreamWriter(stream, StandardCharsets.UTF_8)));
  }

  public static CsvWriter fromFile(File file) {
    try {
      return fromStream(new FileOutputStream(file));
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public CsvWriter(Writer writer) {
    this.writer = new com.univocity.parsers.csv.CsvWriter(writer, SETTINGS);
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
    writer.writeRow(row);
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
