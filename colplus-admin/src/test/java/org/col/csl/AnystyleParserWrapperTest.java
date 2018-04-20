package org.col.csl;

import static com.fasterxml.jackson.core.util.DefaultIndenter.SYS_LF;
import static org.junit.Assert.assertEquals;
import org.apache.http.impl.client.HttpClients;
import org.col.api.model.CslItemData;
import org.col.api.model.CslType;
import org.junit.Ignore;
import org.junit.Test;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings("static-method")
public class AnystyleParserWrapperTest {

  @Test
  @Ignore
  public void testParse01() throws Exception {
    try (AnystyleParserWrapper parser = new AnystyleParserWrapper(HttpClients.createDefault())) {
      parser.start();
      String ref = "Perec, Georges. A Void. London: The Harvill Press, 1995. p.108.";
      CslItemData item = parser.parse(ref).get();
      System.out.println(pretty(item));
      assertEquals("perec1995a", item.getId());
      assertEquals(CslType.BOOK, item.getType());
      assertEquals("Perec", item.getAuthor()[0].getFamily());
      assertEquals("Georges", item.getAuthor()[0].getGiven());
      assertEquals(1995, item.getIssued().getDateParts()[0][0]);
      assertEquals("108", item.getPage());
      assertEquals("The Harvill Press", item.getPublisher());
      assertEquals("London", item.getPublisherPlace());
      assertEquals("A Void", item.getTitle());
    }
  }

  public static String pretty(Object obj) throws JsonProcessingException {
    ObjectMapper om = new ObjectMapper();
    om.setSerializationInclusion(Include.NON_NULL);
    return om.writer(getPrettyPrinter()).writeValueAsString(obj);
  }

  private static DefaultPrettyPrinter getPrettyPrinter() {
    DefaultPrettyPrinter.Indenter indenter = new DefaultIndenter("    ", SYS_LF);
    DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
    printer.indentObjectsWith(indenter);
    printer.indentArraysWith(indenter);
    return printer;
  }
}
