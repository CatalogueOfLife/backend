package life.catalogue.resources.dataset;

import life.catalogue.api.model.DatasetImport;
import life.catalogue.api.model.Page;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DatasetImportResourceTest {

  /**
   * A release has one attempt, and only on the first page - a client loading older attempts
   * until it gets a short page used to receive the same attempt again for every offset.
   * See https://github.com/CatalogueOfLife/backend/issues/1647
   */
  @Test
  public void releasePage() {
    var attempt = new DatasetImport();
    attempt.setAttempt(633);

    var first = DatasetImportResource.releasePage(attempt, new Page(0, 1));
    assertEquals(List.of(attempt), first.getResult());
    assertEquals(1, first.getTotal());

    var second = DatasetImportResource.releasePage(attempt, new Page(1, 1));
    assertEquals(List.of(), second.getResult());
    assertEquals(1, second.getTotal());

    var none = DatasetImportResource.releasePage(attempt, new Page(0, 0));
    assertEquals(List.of(), none.getResult());
    assertEquals(1, none.getTotal());

    // the metrics row of the project attempt may be gone
    var missing = DatasetImportResource.releasePage(null, new Page(0, 10));
    assertEquals(List.of(), missing.getResult());
    assertEquals(0, missing.getTotal());
  }
}
