package life.catalogue.release;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import life.catalogue.api.model.*;

public class EntityUpdater {

  private final int datasetKey;
  private Int2IntMap sectors;

  public EntityUpdater(int datasetKey) {
    this.datasetKey = datasetKey;
  }

  public void setSectors(Int2IntMap sectors) {
    this.sectors = sectors;
  }

  public <C extends DSID<?> & SectorEntity> void sectorEntity(C obj) {
    if (obj.getSectorKey() != null) {
      obj.setSectorKey(sectors.get((int)obj.getSectorKey()));
    }
  }

}
