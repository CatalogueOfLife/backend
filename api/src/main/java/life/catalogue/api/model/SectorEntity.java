package life.catalogue.api.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 *
 */
public interface SectorEntity extends DatasetScoped {

  @JsonIgnore
  default DSID<Integer> getSectorDSID() {
    return DSID.of(getDatasetKey(), getSectorKey());
  }

  Integer getSectorKey();
  
  void setSectorKey(Integer sectorKey);
  
}
