package life.catalogue.csv;

import life.catalogue.common.io.Resources;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ExcelCsvExtractorTest {

  @Test
  public void extract() throws IOException {
    InputStream in = Resources.stream("xls/Pterophoroidea.xlsx");
    //InputStream in = new FileInputStream("/Users/markus/Downloads/legumes.xlsx");
    Path tempDir = Files.createTempDirectory("col");
    List<File> exports = ExcelCsvExtractor.extract(in, tempDir.toFile());
    assertEquals(9, exports.size());


  }
}