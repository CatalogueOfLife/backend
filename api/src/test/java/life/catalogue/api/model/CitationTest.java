package life.catalogue.api.model;

import life.catalogue.common.date.FuzzyDate;

import java.util.List;

import org.junit.Test;

import de.undercouch.citeproc.csl.CSLType;

import static org.junit.Assert.assertNotNull;

public class CitationTest {

  public static Citation create() {
    Citation c = new Citation();
    c.setType(CSLType.ARTICLE);
    c.setTitle("Corona epidemic forever");
    c.setAuthor(List.of(
      new CslName("Bernd", "Schneider"),
      new CslName("Tim", "Berners Lee")
    ));
    c.setCollectionTitle("Global Pandemics");
    c.setIssn("3456-45x6");
    c.setIssued(FuzzyDate.of(2024, 11));
    return c;
  }

  @Test
  public void toCSL() {
    Citation c = create();
    var csl = c.toCSL();
    assertNotNull(csl);
  }
}