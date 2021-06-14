package life.catalogue.api.model;

import life.catalogue.common.date.FuzzyDate;

import java.util.List;
import java.util.UUID;

import org.junit.Test;

import de.undercouch.citeproc.csl.CSLType;

import static org.junit.Assert.assertNotNull;

public class CitationTest {

  public static Citation create() {
    String key = UUID.randomUUID().toString();
    return create(key, DOI.test(key));
  }
  public static Citation create(String id, DOI doi) {
    Citation c = new Citation();
    c.setId(id);
    c.setDoi(doi);
    c.setType(CSLType.ARTICLE_JOURNAL);
    c.setTitle("Corona epidemic forever");
    c.setAuthor(List.of(
      new CslName("Bernd", "Schneider"),
      new CslName("Tim", "Berners Lee")
    ));
    c.setContainerTitle("Global Pandemics");
    c.setVolume("34");
    c.setIssue("4");
    c.setPage("1345-1412");
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