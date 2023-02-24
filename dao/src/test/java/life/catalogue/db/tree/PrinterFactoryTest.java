package life.catalogue.db.tree;

import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.db.TestDataRule;

import org.gbif.nameparser.api.Rank;

import java.io.StringWriter;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class PrinterFactoryTest {

  @Test
  public void dataset() {
    var p = PrinterFactory.dataset(TextTreePrinter.class, 3, null, new StringWriter());
    assertNotNull(p);

    var ttp = TreeTraversalParameter.all(3, null, "x", null, Rank.GENUS, null, true);
    var p2 = PrinterFactory.dataset(JsonFlatPrinter.class, ttp, null, new StringWriter());
    assertNotNull(p2);
  }
}