package life.catalogue.gbifsync;

import life.catalogue.api.model.DatasetGBIF;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Setting;

import java.net.URI;
import java.time.LocalDateTime;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GbifSyncJobTest {

  private static final String URL = "https://hosted-datasets.gbif.org/gtdb/gtdb_r232_coldp.zip";

  private static DatasetPager.GbifDataset gbif(LocalDateTime modified) {
    return gbif(modified, URL, DataFormat.COLDP);
  }

  private static DatasetPager.GbifDataset gbif(LocalDateTime modified, String access, DataFormat format) {
    DatasetPager.GbifDataset d = new DatasetPager.GbifDataset();
    d.modified = modified;
    if (access != null) {
      d.settings.put(Setting.DATA_ACCESS, URI.create(access));
    }
    if (format != null) {
      d.settings.put(Setting.DATA_FORMAT, format);
    }
    return d;
  }

  private static DatasetGBIF existing(LocalDateTime stored) {
    return existing(stored, URL, "coldp");
  }

  private static DatasetGBIF existing(LocalDateTime stored, String access, String format) {
    DatasetGBIF d = new DatasetGBIF();
    d.setGbifModified(stored);
    d.setDataAccess(access);
    d.setDataFormat(format);
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

  /**
   * GBIF does not bump a datasets modified timestamp when only an endpoint changes, so a differing
   * data access url or format must beat the watermark. This is the GTDB case: the registry record was
   * last modified 2026-01-28 while its COLDP endpoint only appeared on 2026-08-25.
   */
  @Test
  public void isUnchangedWithChangedAccess() {
    LocalDateTime t = LocalDateTime.of(2026, 1, 28, 13, 2, 57);
    // access url replaced but the dataset timestamp stands still -> changed
    assertFalse(GbifSyncJob.isUnchanged(gbif(t), existing(t, "https://hosted-datasets.gbif.org/gtdb/gtdb_r226.zip", "dwca")));
    // only the format changed, same url -> changed
    assertFalse(GbifSyncJob.isUnchanged(gbif(t), existing(t, URL, "dwca")));
    // nothing stored yet -> changed
    assertFalse(GbifSyncJob.isUnchanged(gbif(t), existing(t, null, null)));
    // identical access and timestamp -> unchanged
    assertTrue(GbifSyncJob.isUnchanged(gbif(t), existing(t)));
  }

  /**
   * A locked dataset is never updated, so a permanent access difference must not re-flag it on every run.
   */
  @Test
  public void isUnchangedWhenLocked() {
    LocalDateTime t = LocalDateTime.of(2026, 1, 28, 13, 2, 57);
    var locked = existing(t, "https://hosted-datasets.gbif.org/gtdb/gtdb_r226.zip", "dwca");
    locked.setGbifSyncLock(true);
    assertTrue(GbifSyncJob.isUnchanged(gbif(t), locked));
    // but a real registry change still gets through so the watermark is recorded
    assertFalse(GbifSyncJob.isUnchanged(gbif(t.plusSeconds(1)), locked));
  }

  /**
   * The stored format is the Jackson serialised enum name, i.e. lower case with spaces instead of underscores.
   */
  @Test
  public void dataFormatSerialisation() {
    LocalDateTime t = LocalDateTime.of(2026, 6, 9, 20, 0);
    assertFalse(GbifSyncJob.dataAccessDiffers(gbif(t, URL, DataFormat.COLDP), existing(t, URL, "coldp")));
    assertFalse(GbifSyncJob.dataAccessDiffers(gbif(t, URL, DataFormat.DWCA), existing(t, URL, "dwca")));
    assertFalse(GbifSyncJob.dataAccessDiffers(gbif(t, URL, DataFormat.TEXT_TREE), existing(t, URL, "text tree")));
    assertTrue(GbifSyncJob.dataAccessDiffers(gbif(t, URL, DataFormat.COLDP), existing(t, URL, "COLDP")));
  }
}
