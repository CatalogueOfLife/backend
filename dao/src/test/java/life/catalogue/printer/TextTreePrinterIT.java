package life.catalogue.printer;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TreeTraversalParameter;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Users;
import life.catalogue.common.io.Resources;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.dao.TaxonCounter;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.db.mapper.SectorMapperTest;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TextTreePrinterIT {
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @Rule
  public final TestDataRule testDataRule = TestDataRule.tree2();

  @Test
  public void print() throws IOException {
    Writer writer = new StringWriter();
    int count = PrinterFactory.dataset(TextTreePrinter.class, testDataRule.testData.key, SqlSessionFactoryRule.getSqlSessionFactory(), writer).print();
    assertEquals(25, count);
    String expected = UTF8IoUtils.readString(Resources.stream("trees/tree2.tree"));
    assertEquals(expected, writer.toString());
  }

  @Test
  public void printWithCounts() throws IOException {
    Writer writer = new StringWriter();
    AtomicInteger cnt = new AtomicInteger(1);
    // silly species counter that counts the number it has been called instead
    TaxonCounter counter = new TaxonCounter() {
      @Override
      public int count(DSID<String> taxonID, Rank countRank) {
        return cnt.getAndIncrement();
      }
    };
    var p = PrinterFactory.dataset(TextTreePrinter.class, TreeTraversalParameter.datasetNoSynonyms(testDataRule.testData.key),
      Set.of(Rank.FAMILY, Rank.GENUS), null, Rank.SPECIES, counter, SqlSessionFactoryRule.getSqlSessionFactory(), writer
    );
    p.showIDs();
    p.showExtendedInfos();
    int count = p.print();
    System.out.println(writer);
    assertEquals(5, count);
    String expected = UTF8IoUtils.readString(Resources.stream("trees/treeWithCounts.tree"));
    assertEquals(expected, writer.toString());

    // test with extinct
    for (boolean extinct : List.of(true, false)) {
      writer = new StringWriter();
      var ttp = TreeTraversalParameter.dataset(testDataRule.testData.key);
      ttp.setSynonyms(false);
      p = PrinterFactory.dataset(TextTreePrinter.class, ttp, Set.of(Rank.FAMILY, Rank.GENUS),
        extinct, null, null, SqlSessionFactoryRule.getSqlSessionFactory(), writer
      );
      count = p.print();
      System.out.println(writer);
      assertEquals(extinct ? 0 : 5, count);
    }
  }

  /**
   * A usage with a sector cuts off its entire subtree, also the descendants without a sector.
   */
  @Test
  public void sectorless() throws Exception {
    final int key = testDataRule.testData.key;
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var s = SectorMapperTest.create(DSID.of(key, "t6"), DSID.of(Datasets.COL, "x"));
      s.applyUser(Users.TESTER);
      session.getMapper(SectorMapper.class).create(s);
      // Felidae, with all its descendants left without a sector, and the synonym Alopsis of Canis
      try (var st = session.getConnection().createStatement()) {
        st.execute("UPDATE name_usage SET sector_key=" + s.getId() + " WHERE dataset_key=" + key + " AND id IN ('t6','s21')");
      }
    }

    String full = UTF8IoUtils.readString(Resources.stream("trees/tree2.tree"));
    StringBuilder expected = new StringBuilder();
    boolean felidae = false;
    for (String line : full.split("\n")) {
      if (line.trim().startsWith("Felidae")) {
        felidae = true;
      } else if (felidae && line.startsWith("          ") && !line.startsWith("           ")) {
        felidae = false; // next sibling of Felidae
      }
      if (!felidae && !line.trim().startsWith("=Alopsis")) {
        expected.append(line).append("\n");
      }
    }

    Writer writer = new StringWriter();
    var ttp = TreeTraversalParameter.dataset(key);
    ttp.setSectorless(true);
    int count = PrinterFactory.dataset(TextTreePrinter.class, ttp, null, null, null, null, SqlSessionFactoryRule.getSqlSessionFactory(), writer).print();
    assertEquals(expected.toString(), writer.toString());
    assertEquals(25 - 9, count);

    // starting below the sectored family
    writer = new StringWriter();
    ttp.setTaxonID("t5");
    count = PrinterFactory.dataset(TextTreePrinter.class, ttp, null, null, null, null, SqlSessionFactoryRule.getSqlSessionFactory(), writer).print();
    assertEquals(6, count); // Canidae, Canis, =Lupulus and 3 species

    // starting at the sectored family itself prints nothing
    writer = new StringWriter();
    ttp.setTaxonID("t6");
    count = PrinterFactory.dataset(TextTreePrinter.class, ttp, null, null, null, null, SqlSessionFactoryRule.getSqlSessionFactory(), writer).print();
    assertEquals(0, count);
  }

}