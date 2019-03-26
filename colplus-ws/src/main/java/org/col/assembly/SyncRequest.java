package org.col.assembly;

import java.util.Objects;

public class SyncRequest {
  private int sectorKey;

  public int getSectorKey() {
    return sectorKey;
  }

  public void setSectorKey(int sectorKey) {
    this.sectorKey = sectorKey;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SyncRequest that = (SyncRequest) o;
    return sectorKey == that.sectorKey;
  }

  @Override
  public int hashCode() {
    return Objects.hash(sectorKey);
  }
}
