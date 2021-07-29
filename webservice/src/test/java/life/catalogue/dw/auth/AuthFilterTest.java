package life.catalogue.dw.auth;

import java.net.URI;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AuthFilterTest {

  @Test
  public void requestedDataset() {
    assertNull(AuthFilter.requestedDataset(URI.create("")));
    assertNull(AuthFilter.requestedDataset(URI.create("/")));
    assertNull(AuthFilter.requestedDataset(URI.create("/dataset")));
    assertNull(AuthFilter.requestedDataset(URI.create("dataset")));
    assertNull(AuthFilter.requestedDataset(URI.create("/dataset/me")));
    assertNull(AuthFilter.requestedDataset(URI.create("/datasets/1234")));
    assertNull(AuthFilter.requestedDataset(URI.create("dataset/1234")));
    assertNull(AuthFilter.requestedDataset(URI.create("/dataset1234")));

    assertEquals((Integer) 1234, AuthFilter.requestedDataset(URI.create("/dataset/1234")));
    assertEquals((Integer) 1234, AuthFilter.requestedDataset(URI.create("/dataset/1234")));
    assertEquals((Integer) 1234, AuthFilter.requestedDataset(URI.create("/dataset/1234/")));
    assertEquals((Integer) 1234, AuthFilter.requestedDataset(URI.create("/dataset/1234/name")));
  }

}