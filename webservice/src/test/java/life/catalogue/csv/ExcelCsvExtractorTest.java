package life.catalogue.csv;

import life.catalogue.common.io.Resources;
import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.*;

public class ExcelCsvExtractorTest {

  @Test
  public void extract() throws IOException {
    InputStream in = Resources.stream("xls/Pterophoroidea.xlsx");
    Path tempDir = Files.createTempDirectory("col");
    List<File> exports = ExcelCsvExtractor.extract(in, tempDir.toFile());
    assertEquals(9, exports.size());
  }
}