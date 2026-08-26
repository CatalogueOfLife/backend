package life.catalogue.api.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class DatasetGBIF extends DatasetSimple {
  private UUID gbifKey;
  // the GBIF registry modified timestamp we last synced, used to skip unchanged datasets
  private LocalDateTime gbifModified;
  // the data access URL and format we have stored, taken from the dataset settings.
  // GBIF endpoints are sub entities and changing one does not bump the GBIF dataset modified timestamp,
  // so these are compared by value to spot access changes the gbifModified watermark cannot see.
  private String dataAccess;
  private String dataFormat;
  // true if Setting.GBIF_SYNC_LOCK is enabled, i.e. the dataset is never updated by a GBIF sync
  private boolean gbifSyncLock;

  public UUID getGbifKey() {
    return gbifKey;
  }

  public void setGbifKey(UUID gbifKey) {
    this.gbifKey = gbifKey;
  }

  public LocalDateTime getGbifModified() {
    return gbifModified;
  }

  public void setGbifModified(LocalDateTime gbifModified) {
    this.gbifModified = gbifModified;
  }

  public String getDataAccess() {
    return dataAccess;
  }

  public void setDataAccess(String dataAccess) {
    this.dataAccess = dataAccess;
  }

  public String getDataFormat() {
    return dataFormat;
  }

  public void setDataFormat(String dataFormat) {
    this.dataFormat = dataFormat;
  }

  public boolean isGbifSyncLock() {
    return gbifSyncLock;
  }

  public void setGbifSyncLock(boolean gbifSyncLock) {
    this.gbifSyncLock = gbifSyncLock;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DatasetGBIF)) return false;
    if (!super.equals(o)) return false;

    DatasetGBIF that = (DatasetGBIF) o;
    return gbifSyncLock == that.gbifSyncLock
           && Objects.equals(gbifKey, that.gbifKey)
           && Objects.equals(gbifModified, that.gbifModified)
           && Objects.equals(dataAccess, that.dataAccess)
           && Objects.equals(dataFormat, that.dataFormat);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), gbifKey, gbifModified, dataAccess, dataFormat, gbifSyncLock);
  }
}
