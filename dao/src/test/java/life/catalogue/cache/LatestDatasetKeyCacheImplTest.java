package life.catalogue.cache;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LatestDatasetKeyCacheImplTest {

  /**
   * COL releases use the alias convention COLyy[.m][ XR], e.g. COL00, COL26, COL26.5, COL26 XR, COL26.5 XR.
   * Extended releases carry a trailing " XR" suffix - there is never an "XCOL" prefix.
   */
  @Test
  public void colReleaseAlias() {
    // annual
    assertEquals("COL26", LatestDatasetKeyCacheImpl.colReleaseAlias(26, 0, false));
    assertEquals("COL26 XR", LatestDatasetKeyCacheImpl.colReleaseAlias(26, 0, true));
    // monthly
    assertEquals("COL26.5", LatestDatasetKeyCacheImpl.colReleaseAlias(26, 5, false));
    assertEquals("COL26.5 XR", LatestDatasetKeyCacheImpl.colReleaseAlias(26, 5, true));
    // earliest possible year keeps two digits
    assertEquals("COL00", LatestDatasetKeyCacheImpl.colReleaseAlias(0, 0, false));
  }
}
