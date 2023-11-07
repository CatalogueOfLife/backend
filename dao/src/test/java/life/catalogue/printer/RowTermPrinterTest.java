package life.catalogue.printer;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.common.io.TabReader;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.TaxonCounter;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;
import org.gbif.nameparser.api.Rank;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;

public class RowTermPrinterTest {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final TestDataRule testDataRule = TestDataRule.tree();
  
  @Test
  public void coldp() throws IOException {
    print(ColdpPrinter.TSV.class);
  }

  @Test
  public void dwca() throws IOException {
    print(DwcaPrinter.TSV.class);
  }

  public void print(Class<? extends AbstractPrinter> clazz) throws IOException {
    Writer writer = new StringWriter();
    TaxonCounter taxonCounter = new TaxonCounter() {
      @Override
      public int count(DSID<String> taxonID, Rank countRank) {
        return 999;
      }
    };
    int count = PrinterFactory.dataset(clazz, TreeTraversalParameter.dataset(TestDataRule.TREE.key), null, Rank.SPECIES, taxonCounter, SqlSessionFactoryRule.getSqlSessionFactory(), writer).print();
    assertEquals(24, count);
    System.out.println(writer);

    // test if we can read it back in again
    TabReader reader = TabReader.tab(UTF8IoUtils.readerFromString(writer.toString()), 0);
    AtomicInteger counter = new AtomicInteger();
    reader.forEach(r -> counter.incrementAndGet());
    assertEquals(25, counter.get());
  }
}