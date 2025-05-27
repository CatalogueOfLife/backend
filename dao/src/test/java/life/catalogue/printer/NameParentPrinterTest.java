package life.catalogue.printer;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.common.io.Resources;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.TaxonCounter;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.api.Rank;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.*;

public class NameParentPrinterTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.tree();

  @Test
  public void print() throws IOException {
    Writer writer = new StringWriter();
    NameParentPrinter printer = PrinterFactory.dataset(NameParentPrinter.class, TreeTraversalParameter.dataset(TestDataRule.TREE.key), null, null, Rank.SPECIES, null, SqlSessionFactoryRule.getSqlSessionFactory(), writer);
    printer.setFilter(sn -> sn.getLabel().startsWith("Canis"));

    int count = printer.print();
    assertEquals(20, count);
    System.out.println(writer);
    String expected = UTF8IoUtils.readString(Resources.stream("trees/sine-canis.txt"));
    assertEquals(expected, writer.toString());
  }
}