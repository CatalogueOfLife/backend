package life.catalogue.jackson;

import life.catalogue.api.model.Citation;

import life.catalogue.api.model.CitationTest;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.*;

@Ignore("METADATA WORK IN PROGRESS")
public class YamlMapperTest {

  @Test
  public void citationRoundtrip() throws Exception {
    Citation cit = CitationTest.create();
    cit.setAccessed(cit.getIssued());
    String json = YamlMapper.MAPPER.writeValueAsString(cit);
    System.out.println(json);
    Citation cit2 = YamlMapper.MAPPER.readValue(json, Citation.class);
    assertEquals(cit2, cit);
  }
}