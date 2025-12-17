package life.catalogue.metadata.coldp;

import life.catalogue.api.model.Identifier;
import life.catalogue.common.io.Resources;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ColdpMetadataParserTest {

  @Test
  public void oldIdentifierMap() throws Exception {
    var d = ColdpMetadataParser.readYAML(Resources.stream("metadata/col.yaml")).get();
    assertEquals(1, d.getIdentifier().size());
    assertEquals(new Identifier("col", "2278"), d.getIdentifier().getFirst());

    d = ColdpMetadataParser.readYAML(Resources.stream("metadata/col-old.yaml")).get();
    assertEquals(1, d.getIdentifier().size());
    assertEquals(new Identifier("col", "2278"), d.getIdentifier().getFirst());
  }

}