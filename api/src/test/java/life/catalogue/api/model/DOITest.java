package life.catalogue.api.model;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.*;

public class DOITest {
  @Test
  public void getUrl() {
    DOI doi = DOI.col("234567");
    assertEquals("10.48580/234567", doi.toString());
    assertEquals("10.48580/234567", doi.getDoiName());
    assertEquals("doi:10.48580/234567", doi.getDoiString());
    assertEquals(URI.create("https://doi.org/10.48580/234567"), doi.getUrl());
  }
}