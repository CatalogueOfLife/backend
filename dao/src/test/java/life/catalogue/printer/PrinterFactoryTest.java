package life.catalogue.printer;

import life.catalogue.api.model.TreeTraversalParameter;

import org.gbif.nameparser.api.Rank;

import java.io.StringWriter;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import static org.junit.Assert.assertNotNull;

public class PrinterFactoryTest {

  @Test
  public void datasetFactorForAllPrinters() {
    Reflections reflections = new Reflections(AbstractPrinter.class.getPackage().getName());
    Set<Class<?>> printerClasses = reflections.get(Scanners.SubTypes.of(AbstractPrinter.class).asClass());
    for (Class<?> pcl : printerClasses) {
      if (!Modifier.isAbstract(pcl.getModifiers())) {
        AbstractPrinter p = PrinterFactory.dataset((Class<? extends AbstractPrinter>)pcl, 3, null, new StringWriter());
        assertNotNull(p);
      }
    }

    var ttp = TreeTraversalParameter.all(3, null, "x", null, Rank.GENUS, null, true);
    var p2 = PrinterFactory.dataset(JsonFlatPrinter.class, ttp, null, new StringWriter());
    assertNotNull(p2);
  }
}