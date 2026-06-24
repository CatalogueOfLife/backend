package life.catalogue.parser;

import org.junit.Test;

import static org.junit.Assert.*;

public class ParsersTest {
  @Test
  public void registryHasEnumVocabs() {
    assertNotNull(Parsers.get("rank"));
    assertNotNull(Parsers.get("country"));
    assertNotNull(Parsers.get("area"));
    assertSame(Parsers.get("rank"), RankParser.PARSER);
    assertTrue(Parsers.names().contains("rank"));
    assertTrue(Parsers.names().contains("area"));
    // scalars and dedicated parsers are NOT part of the shared vocab registry
    assertNull(Parsers.get("name"));
  }
}
