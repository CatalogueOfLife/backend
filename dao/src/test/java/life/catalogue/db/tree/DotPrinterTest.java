package life.catalogue.db.tree;

import life.catalogue.api.model.DSID;
import life.catalogue.common.io.Resources;
import life.catalogue.dao.TaxonCounter;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DotPrinterTest {

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
    int count = PrinterFactory.dataset(DotPrinter.class, TestDataRule.TREE.key, null, true, null, Rank.SPECIES, taxonCounter, PgSetupRule.getSqlSessionFactory(), writer).print();
    assertEquals(24, count);
    System.out.println(writer);
    String expected = IOUtils.toString(Resources.stream("trees/tree.dot"), StandardCharsets.UTF_8);
    assertEquals(expected, writer.toString());
  }
}