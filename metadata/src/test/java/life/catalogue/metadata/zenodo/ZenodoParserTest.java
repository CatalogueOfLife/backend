package life.catalogue.metadata.zenodo;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetWithSettings;
import life.catalogue.common.io.Resources;
import life.catalogue.metadata.eml.EmlParser;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class ZenodoParserTest {

  @Test
  public void parse() throws Exception {
    Optional<DatasetWithSettings> m = ZenodoParser.parse(Resources.stream("metadata/zenodo.json"));
    Dataset d = m.get().getDataset();

    assertEquals(new DOI("10.5281/zenodo.6407053"), d.getDoi());
    assertEquals("Mammal Diversity Database", d.getTitle());
    var nathan = Agent.person("Nathan", "Upham", null, "0000-0001-5412-9342");
    nathan.setOrganisation("Arizona State University");
    nathan.setNote("ProjectLeader");
    assertEquals(nathan, d.getContributor().get(0));

    assertNull(d.getUrl());
  }
}