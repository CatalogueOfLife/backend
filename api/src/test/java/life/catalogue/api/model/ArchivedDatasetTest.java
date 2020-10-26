package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ArchivedDatasetTest {

  @Test
  public void applyPatch() {
    Dataset d = TestEntityGenerator.newDataset("Hallo Spencer");
    Dataset copy = new Dataset(d);
    Dataset patch = new Dataset();

    assertEquals(copy, d);

    d.applyPatch(patch);
    assertEquals(copy, d);

    patch.setVersion("my version");
    copy.setVersion("my version");
    d.applyPatch(patch);
    assertEquals(copy, d);

    patch.setKey(345678);
    d.applyPatch(patch);
    assertEquals(copy, d);

    // other non metadata infos that should not be patched
    patch.setOrigin(DatasetOrigin.RELEASED);
    patch.setType(DatasetType.ARTICLE);
    patch.setSourceKey(1234);
    patch.setImportAttempt(13);
    d.applyPatch(patch);
    assertEquals(copy, d);
  }
}