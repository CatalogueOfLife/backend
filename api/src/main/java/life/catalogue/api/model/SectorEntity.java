package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import javax.annotation.Nullable;

/**
 *
 */
public interface SectorEntity extends DatasetScoped {

  @JsonIgnore
  default DSID<Integer> getSectorDSID() {
    var sk = getSectorKey();
    return sk == null ? null : DSID.of(getDatasetKey(), getSectorKey());
  }

  @Nullable
  Integer getSectorKey();
  
  void setSectorKey(Integer sectorKey);
  
}
