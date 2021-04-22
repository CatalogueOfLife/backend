package life.catalogue.api.event;

import life.catalogue.api.model.Dataset;

public class DatasetChanged extends EntityChanged<Integer, Dataset> {

  private DatasetChanged(Integer key, boolean created, Dataset obj, Dataset old) {
    super(key, obj, old, created, Dataset.class);
  }

  public static DatasetChanged delete(Dataset d){
    return new DatasetChanged(d.getKey(), false, d, null);
  }

  public static DatasetChanged created(Dataset d){
    return new DatasetChanged(d.getKey(), true, d, null);
  }

  public static DatasetChanged change(Dataset d, Dataset old){
    return new DatasetChanged(d.getKey(), false, d, old);
  }
}
