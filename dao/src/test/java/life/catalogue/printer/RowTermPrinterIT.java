package life.catalogue.printer;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.common.io.TabReader;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.TaxonCounter;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RowTermPrinterIT {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final TestDataRule testDataRule = TestDataRule.tree();
  
  @Test
  public void coldp() throws IOException {
    print(ColdpPrinter.TSV.class);
  }

  /**
   * The count column used to be static, so a print without a count rank failed with "speciesCount is not mapped"
   * once any print with a count rank had run.
   */
  @Test
  public void coldpWithAndWithoutCount() throws IOException {
    var withCount = print(ColdpPrinter.TSV.class, Rank.SPECIES);
    assertTrue(withCount.lines().findFirst().orElseThrow().endsWith("speciesCount"));

    var withoutCount = print(ColdpPrinter.TSV.class, null);
    assertFalse(withoutCount.lines().findFirst().orElseThrow().contains("Count"));

    var genusCount = print(ColdpPrinter.TSV.class, Rank.GENUS);
    assertTrue(genusCount.lines().findFirst().orElseThrow().endsWith("genusCount"));
  }

  @Test
  public void dwca() throws IOException {
    print(DwcaPrinter.TSV.class);
  }

  public void print(Class<? extends AbstractPrinter> clazz) throws IOException {
    print(clazz, Rank.SPECIES);
  }

  private String print(Class<? extends AbstractPrinter> clazz, Rank countRank) throws IOException {
    Writer writer = new StringWriter();
    TaxonCounter taxonCounter = new TaxonCounter() {
      @Override
      public int count(DSID<String> taxonID, Rank countRank) {
        return 999;
      }
    };
    int count = PrinterFactory.dataset(clazz, TreeTraversalParameter.dataset(TestDataRule.TREE.key), null, null,
      countRank, taxonCounter, SqlSessionFactoryRule.getSqlSessionFactory(), writer).print();
    assertEquals(24, count);
    System.out.println(writer);

    // test if we can read it back in again
    TabReader reader = TabReader.tab(UTF8IoUtils.readerFromString(writer.toString()), 0);
    AtomicInteger counter = new AtomicInteger();
    reader.forEach(r -> counter.incrementAndGet());
    assertEquals(25, counter.get());
    return writer.toString();
  }
}