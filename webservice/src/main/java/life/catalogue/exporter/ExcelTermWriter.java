package life.catalogue.exporter;

import life.catalogue.common.io.RowWriter;
import life.catalogue.common.io.TermWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.gbif.dwc.terms.Term;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ExcelTermWriter extends TermWriter {
  public static final int MAX_ROWS = 1048574; // IAE: Invalid row number (1048576) outside allowable range (0..1048575)
  public static final int MAX_VALUE_CHARS = 32767; // IAE: The maximum length of cell contents (text) is 32767 characters
  private static final Logger LOG = LoggerFactory.getLogger(ExcelTermWriter.class);

  public ExcelTermWriter(Workbook wb, Term rowType, Term idTerm, List<Term> cols) throws IOException {
    super(new ExcelRowWriter(wb, rowType), rowType, idTerm, cols);
  }

  public static class MaxRowsException extends IOException {
    private final Term rowType;

    public MaxRowsException(Term rowType) {
      super("Export exceeds maximum amount of " + MAX_ROWS + " rows for " + rowType.prefixedName() + " in Excel");
      this.rowType = rowType;
    }
  }

  static class ExcelRowWriter implements RowWriter {
    private final Workbook wb;
    private final Sheet sh;
    private final Term rowType;
    private int rownum = 0;

    ExcelRowWriter(Workbook wb, Term rowType) {
      this.wb = wb;
      sh = wb.createSheet(rowType.simpleName());
      this.rowType = rowType;
    }

    @Override
    public void write(String[] row) throws IOException {
      if (rownum >= MAX_ROWS) {
        throw new MaxRowsException(rowType);
      }
      Row r = sh.createRow(rownum++);
      int col = 0;
      for (String val : row) {
        Cell cell = r.createCell(col++);
        if (val != null && val.length() > MAX_VALUE_CHARS) {
          LOG.warn("Value in row {} exceeds maximum cell content allowed in Excel", rownum);
          val = val.substring(0, MAX_VALUE_CHARS-2);
        }
        cell.setCellValue(val);
      }
    }

    @Override
    public void close() throws IOException {
      // sheets dont need to be closed.
      // we close the workbook at the very end when bundling the archive
    }
  }
}
