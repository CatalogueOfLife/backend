package life.catalogue.api.event;

import life.catalogue.api.model.Dataset;

public class DatasetChanged extends EntityChanged<Integer, Dataset> {

  private DatasetChanged(Integer key, boolean created, Dataset obj) {
    super(key, obj, created, Dataset.class);
  }

  public static DatasetChanged delete(int key){
    return new DatasetChanged(key, false, null);
  }

  public static DatasetChanged created(Dataset d){
    return new DatasetChanged(d.getKey(), true, d);
  }

  public static DatasetChanged change(Dataset d){
    return new DatasetChanged(d.getKey(), false, d);
  }
}
