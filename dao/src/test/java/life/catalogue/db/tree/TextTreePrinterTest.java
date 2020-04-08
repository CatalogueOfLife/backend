package life.catalogue.db.tree;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import life.catalogue.common.io.Resources;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
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
    int count = TextTreePrinter.dataset(TestDataRule.TestData.TREE.key, PgSetupRule.getSqlSessionFactory(), writer).print();
    assertEquals(24, count);
    String expected = IOUtils.toString(Resources.stream("trees/tree.tree"), StandardCharsets.UTF_8);
    assertEquals(expected, writer.toString());
  }
}