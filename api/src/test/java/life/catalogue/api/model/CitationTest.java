package life.catalogue.api.model;

import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.date.FuzzyDate;

import java.util.List;
import java.util.UUID;

import org.junit.Ignore;
import org.junit.Test;

import de.undercouch.citeproc.csl.CSLType;

import static org.junit.Assert.*;

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

  /**
   * EML parser and other tools only have a single citation string to put into the title.
   * Make sure this renders via CSL.
   */
  @Test
  @Ignore
  public void citationStringOnly() {
    assertCitation("Corona epidemic forever.");
    assertCitation("Schneider, B., & Berners Lee, T. (2024). Corona epidemic forever.");
    assertCitation("Schneider, B., & Berners Lee, T. (2024). Corona epidemic forever. Global Pandemics, 34(4), 1345–1412. https://doi.org/10.80631/097d692c-3938-419d-8f2b-7279c3bf0a5a.");
  }

  void assertCitation(String citation) {
    Citation c = Citation.create(citation);
    assertTrue(c.isUnparsed());
    var cite = c.getCitationText();
    System.out.println(cite);
    assertEquals(citation, cite);

    c.setType(CSLType.ARTICLE_JOURNAL);
    assertFalse(c.isUnparsed());
  }
}