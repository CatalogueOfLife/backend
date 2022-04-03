package life.catalogue.api.vocab;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DataFormatTest {

  @Test
  public void getSuffix() {
    for (DataFormat df : DataFormat.values()) {
      assertNotNull(df.getFilename());
      assertNotNull(df.getName());
      assertNotNull(df.getTitle());
      assertEquals(df.getFilename(), df.getFilename().toLowerCase());
    }
  }
}