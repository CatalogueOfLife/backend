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


  public <T extends DSID<?>> void datasetKey(T obj) {
    obj.setDatasetKey(datasetKey);
  }

  public <T extends DSID<?>> void globalKey(T obj) {
    obj.setId(null);
    obj.setDatasetKey(datasetKey);
  }

  public <C extends DSID<?> & SectorEntity> void sectorEntity(C obj) {
    obj.setDatasetKey(datasetKey);
    if (obj.getSectorKey() != null) {
      obj.setSectorKey(sectors.get((int)obj.getSectorKey()));
    }
  }
  public <E extends DatasetScopedEntity<Integer> & VerbatimEntity> void extensionEntity(TaxonExtension<E> obj) {
    obj.getObj().setDatasetKey(datasetKey);
  }

}
