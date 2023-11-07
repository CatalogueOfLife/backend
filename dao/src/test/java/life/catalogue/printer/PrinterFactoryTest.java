package life.catalogue.printer;

import life.catalogue.api.model.TreeTraversalParameter;

import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;

import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import static org.junit.Assert.assertNotNull;

public class PrinterFactoryTest {
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.tree();

  @Test
  public void datasetFactorForAllPrinters() throws IOException {
    Reflections reflections = new Reflections(AbstractPrinter.class.getPackage().getName());
    Set<Class<?>> printerClasses = reflections.get(Scanners.SubTypes.of(AbstractPrinter.class).asClass());
    for (Class<?> pcl : printerClasses) {
      if (!Modifier.isAbstract(pcl.getModifiers())) {
        var strw = new StringWriter();
        AbstractPrinter p = PrinterFactory.dataset((Class<? extends AbstractPrinter>)pcl, testDataRule.testData.key, SqlSessionFactoryRule.getSqlSessionFactory(), strw);
        int cnt = p.print();
        System.out.println("\n### " + pcl.getSimpleName());
        System.out.println("count=" + cnt);
        System.out.println(strw);
        assertNotNull(strw.toString());
      }
    }

    var ttp = TreeTraversalParameter.all(3, null, "x", null, Rank.GENUS, null, true);
    var p2 = PrinterFactory.dataset(JsonFlatPrinter.class, ttp, null, new StringWriter());
    assertNotNull(p2);
  }
}