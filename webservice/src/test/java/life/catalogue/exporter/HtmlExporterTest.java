package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.io.StringWriter;
import java.io.Writer;

public class HtmlExporterTest {
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public TestDataRule testDataRule = TestDataRule.fish();

  @Test
  public void print() throws Exception {
    WsServerConfig cfg = new WsServerConfig();
    cfg.exportCss = "file:///Users/markus/code/col/backend/webservice/src/main/resources/exporter/html/catalogue.css";
    //Writer w = UTF8IoUtils.writerFromFile(new File("/Users/markus/Desktop/catalogue.html"));

    Writer w = new StringWriter();
    HtmlExporter exp = HtmlExporter.subtree(TestDataRule.FISH.key, "u4", cfg, PgSetupRule.getSqlSessionFactory(), w);
    exp.print();
    System.out.println("\n\n");
    System.out.println(w.toString());
    System.out.println("\n\n");
  }
}