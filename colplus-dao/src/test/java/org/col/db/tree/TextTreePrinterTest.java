package org.col.db.tree;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.col.common.io.Resources;
import org.col.db.PgSetupRule;
import org.col.db.mapper.TestDataRule;
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