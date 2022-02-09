package org.catalogueoflife.coldp.gen;

import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.usermodel.*;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

public abstract class AbstractXlsSrcGenerator extends AbstractGenerator {
  protected Workbook wb;
  private DataFormatter formatter;
  private FormulaEvaluator evaluator;

  public AbstractXlsSrcGenerator(GeneratorConfig cfg, boolean addMetadata, @Nullable URI downloadUri) throws IOException {
    super(cfg, addMetadata, downloadUri);
  }

  @Override
  protected void prepare() throws IOException {
    wb = WorkbookFactory.create(src);
    formatter = new DataFormatter(Locale.US);
    evaluator = wb.getCreationHelper().createFormulaEvaluator();;
  }

  protected String col(Row row, int column) {
    try {
      Cell cell = row.getCell(column);
      return formatter.formatCellValue(cell, evaluator);
    } catch (FormulaParseException e) {
      LOG.warn("Error evaluating excel formula in sheet {}, row {} and column {}: {}", row.getSheet().getSheetName(), row.getRowNum(), column, e.getMessage());
    }
    return null;
  }

  protected String link(Row row, int column) {
    try {
      Cell cell = row.getCell(column);
      var link = cell.getHyperlink();
      if (link != null) {
        return link.getAddress();
      }
    } catch (FormulaParseException e) {
      LOG.warn("Error evaluating excel formula in sheet {}, row {} and column {}: {}", row.getSheet().getSheetName(), row.getRowNum(), column, e.getMessage());
    }
    return null;
  }
}
