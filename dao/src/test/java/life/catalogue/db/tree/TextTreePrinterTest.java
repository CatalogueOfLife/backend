package life.catalogue.db.tree;

import life.catalogue.api.model.DSID;
import life.catalogue.common.io.Resources;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;

import org.gbif.nameparser.api.Rank;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TextTreePrinterTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final TestDataRule testDataRule = TestDataRule.tree();
  
  @Test
  public void print() throws IOException {
    Writer writer = new StringWriter();
    int count = TextTreePrinter.dataset(TestDataRule.TREE.key, PgSetupRule.getSqlSessionFactory(), writer).print();
    assertEquals(24, count);
    String expected = IOUtils.toString(Resources.stream("trees/tree.tree"), StandardCharsets.UTF_8);
    assertEquals(expected, writer.toString());
  }

  @Test
  public void printWithCounts() throws IOException {
    Writer writer = new StringWriter();
    AtomicInteger cnt = new AtomicInteger(1);
    TaxonCounter counter = new TaxonCounter() {
      @Override
      public int count(DSID<String> taxonID, Rank countRank) {
        return cnt.getAndIncrement();
      }
    };
    int count = TextTreePrinter.dataset(TestDataRule.TREE.key, null, false, null, Rank.SPECIES, counter, PgSetupRule.getSqlSessionFactory(), writer).print();
    assertEquals(20, count);
    System.out.println(writer.toString());
    String expected = IOUtils.toString(Resources.stream("trees/treeWithCounts.tree"), StandardCharsets.UTF_8);
    assertEquals(expected, writer.toString());
  }
}