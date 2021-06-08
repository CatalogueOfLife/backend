package life.catalogue.api.model;

import de.undercouch.citeproc.csl.CSLType;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class CitationTest {

  public static Citation create() {
    Citation c = new Citation();
    c.setType(CSLType.ARTICLE);
    c.setTitle("Corona epidemic forever");
    c.setAuthor(List.of(
      new Agent("Bernd", "Schneider"),
      new Agent("Tim", "Berners Lee")
    ));
    c.setJournal("Global Pandemics");
    c.setIssn("3456-45x6");
    c.setYear("2024");
    c.setMonth("November");
    return c;
  }

  @Test
  public void toCSL() {
    Citation c = create();
    var csl = c.toCSL();
    assertNotNull(csl);
  }
}