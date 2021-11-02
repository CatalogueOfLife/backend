package life.catalogue.api.vocab;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DataFormatTest {

  @Test
  public void getSuffix() {
    for (DataFormat df : DataFormat.values()) {
      System.out.println(df.getSuffix());
    }
    assertEquals("dwca", DataFormat.DWCA.getSuffix());
    assertEquals("texttree", DataFormat.TEXT_TREE.getSuffix());
  }
}