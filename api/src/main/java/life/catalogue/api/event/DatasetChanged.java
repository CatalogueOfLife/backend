package life.catalogue.api.event;

import life.catalogue.api.model.Dataset;

import java.util.HashSet;
import java.util.Set;

public class DatasetChanged extends EntityChanged<Integer, Dataset> {
  public final Set<String> usernamesToInvalidate = new HashSet<String>();

  public DatasetChanged(EventType delete, Integer key, Dataset old, Dataset d, int user) {
    super(delete, key, old, d, user, Dataset.class);
  }

  public static DatasetChanged deleted(Dataset d, int user){
    return new DatasetChanged(EventType.DELETE, d.getKey(), null, d, user);
  }

  public static DatasetChanged created(Dataset d, int user){
    return new DatasetChanged(EventType.CREATE, d.getKey(), d, null, user);
  }

  public static DatasetChanged changed(Dataset d, Dataset old, int user){
    return new DatasetChanged(EventType.UPDATE, d.getKey(), d, old, user);
  }
}
