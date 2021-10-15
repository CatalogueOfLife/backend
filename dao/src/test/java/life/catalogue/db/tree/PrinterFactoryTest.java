package life.catalogue.db.tree;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import java.io.StringWriter;

import static org.junit.Assert.*;

public class PrinterFactoryTest {

  @Test
  public void dataset() {
    var p = PrinterFactory.dataset(TextTreePrinter.class, 3, null, new StringWriter());
    assertNotNull(p);

    var p2 = PrinterFactory.dataset(JsonFlatPrinter.class, 3, "x", true, Rank.GENUS, null, new StringWriter());
    assertNotNull(p2);
  }
}