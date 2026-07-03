package life.catalogue.common.tax.authormap;

import com.fasterxml.jackson.databind.*;
import life.catalogue.common.io.Resources;
import java.io.InputStream;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class WikidataSourceTest {
  @Test
  public void parsesSparqlBindings() throws Exception {
    ObjectMapper om = new ObjectMapper();
    JsonNode json;
    try (InputStream in = Resources.stream("authormap/wikidata-sample.json")) {
      json = om.readTree(in);
    }
    List<AuthorEntry> entries = new WikidataSource().parse(json);
    // one row per author, abbreviation + label aliases, code from which property matched
    AuthorEntry linn = entries.stream().filter(e -> e.aliases().contains("Carl Linnaeus")).findFirst().orElseThrow();
    assertEquals(AuthorCode.BOT, linn.code());               // has botanist abbrev (P428)
    assertTrue(linn.aliases().contains("L."));
    AuthorEntry cuv = entries.stream().filter(e -> e.aliases().contains("Georges Cuvier")).findFirst().orElseThrow();
    assertEquals(AuthorCode.ZOO, cuv.code());                // has zoologist citation (P835)
  }
}
