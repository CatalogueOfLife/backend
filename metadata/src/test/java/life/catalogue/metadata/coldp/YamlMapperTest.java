package life.catalogue.metadata.coldp;

import life.catalogue.api.model.Citation;
import life.catalogue.api.model.CslName;
import life.catalogue.api.model.DOI;
import life.catalogue.common.date.FuzzyDate;

import java.util.List;
import java.util.UUID;

import org.junit.Test;

import de.undercouch.citeproc.csl.CSLType;

import static org.junit.Assert.assertEquals;

public class YamlMapperTest {

  @Test
  public void citationRoundtrip() throws Exception {
    Citation cit = create();
    cit.setAccessed(cit.getIssued());
    String json = YamlMapper.MAPPER.writeValueAsString(cit);
    System.out.println(json);
    Citation cit2 = YamlMapper.MAPPER.readValue(json, Citation.class);
    assertEquals(cit2, cit);
  }

  static Citation create() {
    Citation c = new Citation();
    c.setId(UUID.randomUUID().toString());
    c.setDoi(DOI.test(c.getId()));
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
}