package life.catalogue.printer;

import life.catalogue.common.io.Resources;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DwcTreePrinterTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.tree3();

  @Test
  public void print() throws IOException {
    Writer writer = new StringWriter();
    DwcTreePrinter printer = PrinterFactory.dataset(DwcTreePrinter.class, testDataRule.testData.key, SqlSessionFactoryRule.getSqlSessionFactory(), writer);
    printer.showTaxGroups();
    int count = printer.print();
    assertEquals(34, count);
    String expected = UTF8IoUtils.readString(Resources.stream("trees/dwc-tg-tree.tsv"));
    assertEquals(expected, writer.toString());
  }

}