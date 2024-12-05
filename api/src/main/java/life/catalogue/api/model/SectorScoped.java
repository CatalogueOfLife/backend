package life.catalogue.api.model;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *
 */
public interface SectorScoped extends DatasetScoped {

  @JsonIgnore
  default DSID<Integer> getSectorDSID() {
    var sk = getSectorKey();
    return sk == null ? null : DSID.of(getDatasetKey(), getSectorKey());
  }

  @Nullable
  Integer getSectorKey();
  
  void setSectorKey(Integer sectorKey);

  @JsonIgnore
  Sector.Mode getSectorMode();
  void setSectorMode(Sector.Mode mode);

  @Nullable
  default Boolean isMerged() {
    return getSectorMode() == null ? null : Sector.Mode.MERGE == getSectorMode();
  }
}
