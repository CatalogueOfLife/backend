package life.catalogue.csv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.base.Preconditions;
import life.catalogue.common.io.TabWriter;
import org.apache.poi.ss.formula.FormulaParseException;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class ExcelCsvExtractor {
  private static final Logger LOG = LoggerFactory.getLogger(CsvReader.class);
  private static final String MetadataSheet = "metadata";

  public static List<File> extract(InputStream spreadsheet, File exportFolder) throws IOException {
    // Open the workbook
    Workbook wb = WorkbookFactory.create(spreadsheet);
    ExcelCsvExtractor extractor = new ExcelCsvExtractor(wb);
    return extractor.export(exportFolder);
  }

  private final Workbook wb;
  private final DataFormatter formatter;
  private final FormulaEvaluator evaluator;
  private final List<File> exports = new ArrayList<>();
  private Sheet sheet;
  private int maxCol;
  private Set<Integer> missingCols = new HashSet<>();
  private File folder;
  private ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

  public ExcelCsvExtractor(Workbook wb) {
    this.wb = wb;
    // create the FormulaEvaluator and DataFormatter instances that will be needed to, respectively,
    // force evaluation of forumlae found in cells and create a formatted String encapsulating the cells contents.
    formatter = new DataFormatter(Locale.US);
    evaluator = wb.getCreationHelper().createFormulaEvaluator();;
  }

  public List<File> export(File folder) {
    Preconditions.checkArgument(folder.isDirectory() && folder.exists(), "Export folder needs to be a writable directory");
    this.folder = folder;
    LOG.info("Trying to export {} sheets to {}", wb.getNumberOfSheets(), folder);
    for (Sheet sheet : wb) {
      this.sheet = sheet;
      if (sheet.getSheetName().trim().equalsIgnoreCase(MetadataSheet)) {
        toYAML();
      } else {
        toTSV();
      }
    }
    return exports;
  }

  private void toTSV() {
    // require at least a header row and one record
    if(sheet.getPhysicalNumberOfRows() < 2) {
      LOG.info("No data records in sheet {}", sheet.getSheetName());
      return;
    }

    List<String> header = header(sheet.getRow(0));
    if (header.isEmpty()) {
      LOG.info("No header columns given in sheet {}", sheet.getSheetName());
      return;
    }
    File f = new File(folder, sheet.getSheetName() + ".tsv");
    try (TabWriter writer = TabWriter.fromFile(f)){
      writer.write(header.toArray(new String[0]));
      final String[] data = new String[header.size()];
      final int lastRowNum = sheet.getLastRowNum();

      Row row;
      int counter = 0;
      for(int j = 1; j <= lastRowNum; j++) {
        row = sheet.getRow(j);
        // Check to ensure that a row existed in the sheet as it is possible that one or more rows between other populated rows could be missing - blank
        if (row != null) {
          read(data, row);
          writer.write(data);
          counter++;
        }
      }
      exports.add(f);
      LOG.info("Export sheet {} to TSV {} with {} records", sheet.getSheetName(), f.getAbsoluteFile(), counter);
    } catch (IOException e) {
      LOG.error("Failed to export sheet {} to TSV", sheet.getSheetName(), e);
    }
  }

  private List<String> header(Row row) {
    missingCols.clear();
    List<String> header = new ArrayList<>();
    Cell cell;
    int lastCellNum = row.getLastCellNum();
    for(int i = 0; i <= lastCellNum; i++) {
      cell = row.getCell(i);
      if(cell == null) {
        missingCols.add(i);
      } else {
        header.add(formatter.formatCellValue(cell, evaluator));
        maxCol = i;
      }
    }
    return header;
  }

  private void read(String[] data, Row row) {
    Cell cell;
    int lastCellNum = row.getLastCellNum();
    int dataIdx=0;
    for(int i = 0; i <= maxCol; i++) {
      if (missingCols.contains(i)) {
        // skipped columns without any header
        continue;
      }
      if (i > lastCellNum) {
        data[dataIdx] = null;
      } else {
        cell = row.getCell(i);
        try {
          data[dataIdx] = formatter.formatCellValue(cell, evaluator);
        } catch (FormulaParseException e) {
          LOG.warn("Error evaluating excel formula in sheet {}, row {} and column {}: {}", sheet.getSheetName(), row.getRowNum(), i, e.getMessage());
          data[dataIdx] = null;
        }
      }
      dataIdx++;
    }
  }

  /**
   * Keys in first column, values in subsequent ones
   */
  private void toYAML() {
    // require at one row
    if(sheet.getPhysicalNumberOfRows() < 1) {
      LOG.info("No metadata in sheet {}", sheet.getSheetName());
      return;
    }

    File f = new File(folder, sheet.getSheetName() + ".yaml");
    try (FileOutputStream fos = new FileOutputStream(f);
         SequenceWriter sw = yamlMapper.writerWithDefaultPrettyPrinter().writeValues(fos)
    ){
      final ObjectNode root = yamlMapper.createObjectNode();
      root.put("title", "Ernest living");

      Row row;
      Cell cell;
      int counter = 0;
      List<String> values = new ArrayList<>();
      final int lastRowNum = sheet.getLastRowNum();
      for(int r = 0; r <= lastRowNum; r++) {
        row = sheet.getRow(r);
        // Check to ensure that a row existed in the sheet as it is possible that one or more rows between other populated rows could be missing - blank
        values.clear();
        if (row != null) {
          final int maxCol = row.getLastCellNum();
          if (maxCol > 1) {
            cell = row.getCell(0);
            if (cell != null) {
              String key = formatter.formatCellValue(cell, evaluator);
              for(int c = 1; c <= maxCol; c++) {
                cell = row.getCell(c);
                if (cell != null) {
                  values.add(formatter.formatCellValue(cell, evaluator));
                }
              }
              // remove empty ones
              values.removeIf(String::isBlank);
              if (values.size() == 1) {
                root.put(key, values.get(0));
              } else if (values.size() > 1) {
                ArrayNode array = yamlMapper.createArrayNode();
                values.forEach(array::add);
                root.set(key, array);
              }
              counter++;
            }
          }
        }
      }
      sw.write(root);
      exports.add(f);
      LOG.info("Export sheet {} to YAML {} with {} fields", sheet.getSheetName(), f.getAbsoluteFile(), counter);
    } catch (IOException e) {
      LOG.error("Failed to export sheet {} to YAML", sheet.getSheetName(), e);
    }
  }

}
