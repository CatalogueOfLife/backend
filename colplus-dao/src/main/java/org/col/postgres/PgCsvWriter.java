package org.col.postgres;

import java.io.File;
import java.io.IOException;

import org.col.common.io.TabWriter;

/**
 * A writer for native postgres copy commands that converts a pg query result into a CSV file efficiently.
 */
public class PgCsvWriter extends TabMapperBase {
  private final TabWriter writer;
  
  public PgCsvWriter(int rowSize, File f) {
    super(rowSize);
    writer = TabWriter.fromFile(f);
  }
  
  @Override
  protected void addRow(String[] row) throws IOException {
    writer.write(row);
  }
  
  @Override
  public void close() throws IOException {
    writer.close();
  }
  
}
