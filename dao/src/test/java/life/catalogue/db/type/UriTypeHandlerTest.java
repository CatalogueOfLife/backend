package life.catalogue.db.type;

import junit.framework.TestCase;

import java.net.URI;

public class UriTypeHandlerTest extends TestCase {

  public void testNullScheme() throws Exception {
    // used for patching datasets
    assertNull(UriTypeHandler.toURI(null));
    assertNull(UriTypeHandler.toURI(""));
    assertEquals(URI.create("null:null"), UriTypeHandler.toURI("null:null"));
  }
}