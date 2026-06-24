package life.catalogue.resources.parser.openrefine;

import life.catalogue.resources.matching.openrefine.OpenRefineModel;

import java.net.URI;

import org.junit.Test;

import jakarta.ws.rs.NotFoundException;

import static org.junit.Assert.*;

public class VocabReconciliationResourceTest {
  private final VocabReconciliationResource res =
    new VocabReconciliationResource(URI.create("http://api"), URI.create("http://clb"));

  @Test(expected = NotFoundException.class)
  public void reservedTypeIs404() {
    res.root("name", null); // name handled by dedicated resource
  }

  @Test(expected = NotFoundException.class)
  public void unknownTypeIs404() {
    res.root("does-not-exist", null);
  }

  @Test
  public void enumManifestHasSuggest() {
    Object m = res.root("rank", null);
    assertTrue(m instanceof OpenRefineModel.Manifest);
    assertNotNull(((OpenRefineModel.Manifest) m).suggest);
  }
}
