package life.catalogue.api.model;

import java.net.URI;
import java.util.function.Supplier;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class DOITest {

  @Test
  public void getUrl() {
    DOI doi = DOI.col("234567");
    assertEquals("10.48580/234567", doi.toString());
    assertEquals("10.48580/234567", doi.getDoiName());
    assertEquals("doi:10.48580/234567", doi.getDoiString());
    assertEquals(URI.create("https://doi.org/10.48580/234567"), doi.getUrl());
  }

  @Test
  public void datasetKeys() {
    DOI doi = DOI.dataset(DOI.TEST_PREFIX, 1010);
    assertEquals("10.80631/d37v", doi.toString());
    assertEquals(1010, doi.datasetKey());
    assertIAE(doi::sourceDatasetKey);

    doi = DOI.datasetSource(DOI.TEST_PREFIX, 3, 1010);
    assertEquals("10.80631/d5-37v", doi.toString());
    assertEquals(DSID.of(3,1010), doi.sourceDatasetKey());
    assertIAE(doi::datasetKey);

    doi = DOI.test("1010");
    assertEquals("10.80631/1010", doi.toString());
    assertIAE(doi::datasetKey);
    assertIAE(doi::sourceDatasetKey);
  }

  void assertIAE(Supplier supplier) {
    try {
      var result = supplier.get();
      fail("IllegalArgumentException expected but got "+result);
    } catch (IllegalArgumentException e) {
      // good
    }
  }
}