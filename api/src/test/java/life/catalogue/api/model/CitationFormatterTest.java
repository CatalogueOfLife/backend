package life.catalogue.api.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CitationFormatterTest {

  @Test
  public void fallbackWhenUnregistered() {
    CitationFormatter.register(null);
    Citation c = Citation.create("Some title");
    assertNull(c.getCitation());       // no formatter -> null, no exception
    assertNull(c.getCitationText());
  }

  @Test
  public void delegatesToRegisteredFormatter() {
    CitationFormatter.register(new CitationFormatter() {
      public String citationHtml(Citation c) { return "H:" + c.getTitle(); }
      public String citationText(Citation c) { return "T:" + c.getTitle(); }
      public String citationHtml(Dataset d) { return "DH"; }
      public String citationText(Dataset d) { return "DT"; }
    });
    try {
      Citation c = Citation.create("Title");
      assertEquals("H:Title", c.getCitation());
      assertEquals("T:Title", c.getCitationText());
    } finally {
      CitationFormatter.register(null);
    }
  }
}
