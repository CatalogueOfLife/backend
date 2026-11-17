package life.catalogue.api.model;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.License;

import java.util.UUID;

import org.junit.Test;

import static org.junit.Assert.*;

public class PublisherMetricsTest {

  @Test
  public void addAndSerialise() throws Exception {
    var m = new PublisherMetrics(UUID.randomUUID());
    m.add(DatasetOrigin.EXTERNAL, DatasetType.TAXONOMIC, License.CC_BY, false, 3, 100);
    m.add(DatasetOrigin.EXTERNAL, DatasetType.NOMENCLATURAL, null, true, 2, 10);
    m.add(DatasetOrigin.PROJECT, DatasetType.TAXONOMIC, License.CC_BY, false, 1, 0);
    assertEquals(6, m.getDatasets());
    assertEquals(2, m.getPrivate());
    assertEquals(110, m.getUsages());
    assertEquals(5, (int) m.getOrigin().get(DatasetOrigin.EXTERNAL));
    assertEquals(4, (int) m.getType().get(DatasetType.TAXONOMIC));
    assertEquals(4, (int) m.getLicense().get(License.CC_BY));
    assertEquals(1, m.getLicense().size());

    String json = ApiModule.MAPPER.writeValueAsString(m);
    assertTrue(json.contains("\"private\":2"));
    assertTrue(json.contains("\"external\":5"));
    assertTrue(json.contains("\"taxonomic\":4"));
  }
}
