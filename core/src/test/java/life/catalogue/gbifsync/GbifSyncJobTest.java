package life.catalogue.gbifsync;

import life.catalogue.api.model.DatasetGBIF;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.api.vocab.Setting;
import life.catalogue.api.vocab.Users;
import life.catalogue.config.GbifConfig;
import life.catalogue.dao.DatasetDao;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Test;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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

  /**
   * A dataset hosted by ChecklistBank's own GBIF installation is published from ChecklistBank: the sync must neither
   * create nor update it, even if its registry record changed, and must not link it automatically.
   * It still reports the key of an existing dataset, so the full sync counts it as seen.
   */
  @Test
  public void hostedIsNeverTouched() throws Exception {
    DatasetDao dao = mock(DatasetDao.class);
    SqlSessionFactory factory = mock(SqlSessionFactory.class);
    GbifConfig cfg = new GbifConfig();
    Client client = ClientBuilder.newClient();
    try {
      var job = new GbifSyncJob(cfg, client, dao, factory, new GbifRegistryCache(client, cfg), Users.GBIF_SYNC, false);
      LocalDateTime t = LocalDateTime.of(2026, 11, 11, 10, 0);
      var hosted = gbif(t.plusDays(1), "https://api.checklistbank.org/dataset/7/archive.zip", DataFormat.TEXT_TREE);
      hosted.hosted = true;
      hosted.dataset.setGbifKey(UUID.randomUUID());

      assertNull(job.sync(hosted, null));
      var existing = existing(t);
      existing.setKey(7);
      assertEquals(7, (int) job.sync(hosted, existing));
      verifyNoInteractions(dao, factory);
    } finally {
      client.close();
    }
  }
}
