package life.catalogue.db.tree;

import org.gbif.nameparser.api.Rank;

import java.io.StringWriter;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class PrinterFactoryTest {

  @Test
  public void dataset() {
    var p = PrinterFactory.dataset(TextTreePrinter.class, 3, null, new StringWriter());
    assertNotNull(p);

    var p2 = PrinterFactory.dataset(JsonFlatPrinter.class, 3, "x", true, Rank.GENUS, null, new StringWriter());
    assertNotNull(p2);
  }
}