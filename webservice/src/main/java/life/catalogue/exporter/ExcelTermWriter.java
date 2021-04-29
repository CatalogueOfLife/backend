package life.catalogue.exporter;

import life.catalogue.common.io.RowWriter;
import life.catalogue.common.io.TermWriter;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellReference;
import org.gbif.dwc.terms.Term;

import java.io.IOException;
import java.util.List;

public class ExcelTermWriter extends TermWriter {

  public ExcelTermWriter(Workbook wb, Term rowType, Term idTerm, List<Term> cols) throws IOException {
    super(new ExcelRowWriter(wb, rowType), rowType, idTerm, cols);
  }

  static class ExcelRowWriter implements RowWriter {
    private final Workbook wb;
    private final Sheet sh;
    private int rownum = 0;

    ExcelRowWriter(Workbook wb, Term rowType) {
      this.wb = wb;
      sh = wb.createSheet(rowType.simpleName());
    }

    @Override
    public void write(String[] row) throws IOException {
      Row r = sh.createRow(rownum++);
      int col = 0;
      for (String val : row) {
        Cell cell = r.createCell(col++);
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
