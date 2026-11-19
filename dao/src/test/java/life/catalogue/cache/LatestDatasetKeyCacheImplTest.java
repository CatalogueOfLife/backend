package life.catalogue.cache;

import life.catalogue.api.model.DatasetSimple;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.junit.DatasetInfoCacheMockRule;

import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class LatestDatasetKeyCacheImplTest {

  @Rule
  public final DatasetInfoCacheMockRule infoRule = new DatasetInfoCacheMockRule();

  /**
   * The DOI of a deleted release is hidden and pointed back to ChecklistBank, which asks whether it was the latest
   * release. It used to throw NotFoundException for the deleted dataset, so the DOI command failed on every attempt.
   */
  @Test
  public void deletedReleaseIsNotLatest() {
    var d = new DatasetSimple();
    d.setKey(264436);
    d.setOrigin(DatasetOrigin.RELEASE);
    d.setSourceKey(9822);
    d.setDeleted(true);
    infoRule.put(d);
    assertFalse(new LatestDatasetKeyCacheImpl(infoRule.getFactory()).isLatestRelease(264436));
  }

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
