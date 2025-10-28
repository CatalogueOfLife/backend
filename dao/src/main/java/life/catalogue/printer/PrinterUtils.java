package life.catalogue.printer;

import life.catalogue.api.model.TreeTraversalParameter;

import org.apache.ibatis.session.SqlSessionFactory;

import java.io.StringWriter;
import java.io.Writer;

public class PrinterUtils {

  public static String print(int datasetKey, boolean showIDs, SqlSessionFactory factory) throws Exception {
    Writer writer = new StringWriter();
    TreeTraversalParameter ttp = TreeTraversalParameter.dataset(datasetKey);
    var printer = PrinterFactory.dataset(TextTreePrinter.class, ttp, factory, writer);
    if (showIDs) {
      printer.showIDs();
    }
    printer.print();
    String tree = writer.toString();
    System.out.println("***** dataset "+datasetKey+" *****\n" + tree + "\n\n");
    return tree;
  }
}
