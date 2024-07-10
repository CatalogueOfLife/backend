package life.catalogue.printer;

import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.common.io.Resources;
import life.catalogue.common.io.UTF8IoUtils;
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
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class TextTreePrinter2Test {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final TestDataRule testDataRule = TestDataRule.tree3();

  @Test
  public void print() throws IOException {
    Writer writer = new StringWriter();
    int count = PrinterFactory.dataset(TextTreePrinter.class, testDataRule.testData.key, SqlSessionFactoryRule.getSqlSessionFactory(), writer).print();
    assertEquals(34, count);
    String expected = UTF8IoUtils.readString(Resources.stream("trees/tree3.tree"));
    assertEquals(expected, writer.toString());
  }

  @Test
  public void printWithCounts() throws IOException {
    Writer writer = new StringWriter();
    var p = PrinterFactory.dataset(TextTreePrinter.class, TreeTraversalParameter.datasetNoSynonyms(testDataRule.testData.key),
      Set.of(Rank.FAMILY, Rank.GENUS), null,
      Rank.SPECIES, null, SqlSessionFactoryRule.getSqlSessionFactory(), writer
    );
    p.showIDs();
    int count = p.print();
    System.out.println(writer);
    assertEquals(9, count);

    // test with extinct
    for (boolean extinct : List.of(true, false)) {
      writer = new StringWriter();
      var ttp = TreeTraversalParameter.dataset(testDataRule.testData.key);
      ttp.setSynonyms(true);
      p = PrinterFactory.dataset(TextTreePrinter.class, ttp, null, extinct, null, null,
        SqlSessionFactoryRule.getSqlSessionFactory(), writer
      );
      p.showIDs();
      count = p.print();
      System.out.println(writer);
      assertEquals(extinct ? 13 : 19, count);
      String expected = UTF8IoUtils.readString(Resources.stream("trees/tree3-" + (extinct ? "extinct" : "extant") + ".tree"));
      assertEquals(expected, writer.toString());
    }
  }

}