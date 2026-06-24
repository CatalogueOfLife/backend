package life.catalogue.resources.matching.openrefine;

import life.catalogue.api.jackson.ApiModule;

import org.gbif.nameparser.api.NomCode;
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

  @Test
  public void buildsNameWithCodeAndClassification() throws Exception {
    String json = "{\"query\":\"Puma concolor\",\"properties\":[" +
      "{\"pid\":\"code\",\"v\":\"ICZN\"},{\"pid\":\"family\",\"v\":\"Felidae\"}]}";
    var q = ApiModule.MAPPER.readValue(json, OpenRefineModel.Query.class);
    var sn = OpenRefineQueries.toSimpleName(q);
    assertEquals(NomCode.ZOOLOGICAL, sn.getCode());
    assertNotNull(sn.getClassification());
    assertEquals(1, sn.getClassification().size());
    assertEquals("Felidae", sn.getClassification().get(0).getName());
    assertEquals(Rank.FAMILY, sn.getClassification().get(0).getRank());
  }
}
