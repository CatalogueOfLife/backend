package life.catalogue.db.tree;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.common.io.Resources;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.TaxonCounter;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NewickPrinterTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.tree();

  @Test
  public void print() throws IOException {
    Writer writer = new StringWriter();
    TaxonCounter taxonCounter = new TaxonCounter() {
      @Override
      public int count(DSID<String> taxonID, Rank countRank) {
        return 999;
      }
    };
    var ttp = TreeTraversalParameter.dataset(TestDataRule.TREE.key);
    int count = PrinterFactory.dataset(NewickPrinter.class, TreeTraversalParameter.datasetNoSynonyms(TestDataRule.TREE.key), null, Rank.SPECIES, taxonCounter, PgSetupRule.getSqlSessionFactory(), writer).print();
    assertEquals(20, count);
    System.out.println(writer);
    String expected = UTF8IoUtils.readString(Resources.stream("trees/tree.newick"));
    assertEquals(expected, writer.toString());

    writer = new StringWriter();
    var printer = PrinterFactory.dataset(NewickPrinter.class, TreeTraversalParameter.datasetNoSynonyms(TestDataRule.TREE.key), null, Rank.SPECIES, taxonCounter, PgSetupRule.getSqlSessionFactory(), writer);
    printer.useExtendedFormat();
    count = printer.print();
    assertEquals(20, count);
    System.out.println(writer);
    expected = UTF8IoUtils.readString(Resources.stream("trees/tree.nhx"));
    assertEquals(expected, writer.toString());
  }
}