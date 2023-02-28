package life.catalogue.db.tree;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.common.io.Resources;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.TaxonCounter;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JsonFlatPrinterTest {
  
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
    int count = PrinterFactory.dataset(JsonFlatPrinter.class, TreeTraversalParameter.dataset(TestDataRule.TREE.key), null, Rank.SPECIES, taxonCounter, SqlSessionFactoryRule.getSqlSessionFactory(), writer).print();
    assertEquals(24, count);
    System.out.println(writer);
    String expected = UTF8IoUtils.readString(Resources.stream("trees/flat.json"));
    // properties label and labelHtml change their order in the serialization. Lets focus on labelHtml
    String got = writer.toString().replaceAll(",\"label\":\"[^\"]+\"", "");
    assertEquals(expected, got);
  }
}