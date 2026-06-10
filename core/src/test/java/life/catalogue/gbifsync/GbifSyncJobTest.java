package life.catalogue.gbifsync;

import life.catalogue.api.model.DatasetGBIF;

import java.time.LocalDateTime;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GbifSyncJobTest {

  private static DatasetPager.GbifDataset gbif(LocalDateTime modified) {
    DatasetPager.GbifDataset d = new DatasetPager.GbifDataset();
    d.modified = modified;
    return d;
  }

  private static DatasetGBIF existing(LocalDateTime stored) {
    DatasetGBIF d = new DatasetGBIF();
    d.setGbifModified(stored);
    return d;
  }

  /**
   * The delta gate decides whether a GBIF dataset can be skipped without hitting the registry or the DB.
   */
  @Test
  public void isUnchanged() {
    LocalDateTime t = LocalDateTime.of(2026, 6, 9, 20, 0);
    // same timestamp -> unchanged, can skip
    assertTrue(GbifSyncJob.isUnchanged(gbif(t), existing(t)));
    // GBIF older than what we stored -> unchanged
    assertTrue(GbifSyncJob.isUnchanged(gbif(t.minusSeconds(1)), existing(t)));
    // GBIF newer than stored -> changed, must process
    assertFalse(GbifSyncJob.isUnchanged(gbif(t.plusSeconds(1)), existing(t)));
    // unknown timestamps -> treat as changed so the dataset is processed once and its watermark recorded
    assertFalse(GbifSyncJob.isUnchanged(gbif(null), existing(t)));
    assertFalse(GbifSyncJob.isUnchanged(gbif(t), existing(null)));
  }
}
