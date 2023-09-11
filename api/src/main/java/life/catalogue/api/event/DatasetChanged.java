package life.catalogue.api.event;

import life.catalogue.api.model.Dataset;

import java.util.HashSet;
import java.util.Set;

public class DatasetChanged extends EntityChanged<Integer, Dataset> {
  public final Set<String> usernamesToInvalidate = new HashSet<String>();

  public DatasetChanged(EventType delete, Integer key, Dataset old, Dataset d) {
    super(delete, key, old, d, Dataset.class);
  }

  public static DatasetChanged deleted(Dataset d){
    return new DatasetChanged(EventType.DELETE, d.getKey(), null, d);
  }

  public static DatasetChanged created(Dataset d){
    return new DatasetChanged(EventType.CREATE, d.getKey(), d, null);
  }

  public static DatasetChanged changed(Dataset d, Dataset old){
    return new DatasetChanged(EventType.UPDATE, d.getKey(), d, old);
  }
}
