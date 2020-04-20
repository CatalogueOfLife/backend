package life.catalogue.api.event;

import life.catalogue.api.model.Dataset;

public class DatasetChanged extends EntityChanged<Integer, Dataset> {

  private DatasetChanged(Integer key, Dataset obj) {
    super(key, obj, Dataset.class);
  }

  public static DatasetChanged delete(int key){
    return new DatasetChanged(key, null);
  }

  public static DatasetChanged change(Dataset d){
    return new DatasetChanged(d.getKey(), d);
  }
}
