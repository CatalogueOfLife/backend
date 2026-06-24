package life.catalogue.resources.matching.openrefine;

import life.catalogue.api.jackson.ApiModule;

import org.gbif.nameparser.api.Rank;

import org.junit.Test;

import static org.junit.Assert.*;

public class OpenRefineQueriesTest {
  @Test
  public void buildsNameWithHints() throws Exception {
    String json = "{\"query\":\"Puma concolor\",\"properties\":[" +
      "{\"pid\":\"authorship\",\"v\":\"Linnaeus, 1771\"},{\"pid\":\"rank\",\"v\":\"species\"}]}";
    var q = ApiModule.MAPPER.readValue(json, OpenRefineModel.Query.class);
    var sn = OpenRefineQueries.toSimpleName(q);
    assertEquals("Puma concolor", sn.getName());
    assertEquals("Linnaeus, 1771", sn.getAuthorship());
    assertEquals(Rank.SPECIES, sn.getRank());
  }

  @Test
  public void blankQueryIsNull() {
    var q = new OpenRefineModel.Query();
    assertNull(OpenRefineQueries.toSimpleName(q));
  }
}
