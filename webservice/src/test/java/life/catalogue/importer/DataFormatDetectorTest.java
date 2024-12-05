package life.catalogue.importer;

import life.catalogue.api.vocab.DataFormat;
import life.catalogue.common.io.Resources;
import life.catalogue.csv.DwcaReader;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DataFormatDetectorTest {

  @Test
  public void detectFormat() {
    assertEquals(DataFormat.DWCA, DataFormatDetector.detectFormat(Resources.toPath("dwca/0")));
    assertEquals(DataFormat.DWCA, DataFormatDetector.detectFormat(Resources.toPath("dwca/plazi1")));

    assertEquals(DataFormat.TEXT_TREE, DataFormatDetector.detectFormat(Resources.toPath("txtree/0")));

    assertEquals(DataFormat.COLDP, DataFormatDetector.detectFormat(Resources.toPath("coldp/0")));
    assertEquals(DataFormat.COLDP, DataFormatDetector.detectFormat(Resources.toPath("coldp/1")));

    assertEquals(DataFormat.ACEF, DataFormatDetector.detectFormat(Resources.toPath("acef/0")));
    assertEquals(DataFormat.ACEF, DataFormatDetector.detectFormat(Resources.toPath("acef/1")));
  }

}