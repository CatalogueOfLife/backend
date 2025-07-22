package life.catalogue.api.event;

import life.catalogue.api.model.Dataset;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class DatasetChanged extends EntityChanged<Integer, Dataset> {
  public final Set<String> usernamesToInvalidate = new HashSet<String>();

  public DatasetChanged() {
  }

  public DatasetChanged(EventType type, Integer key, Dataset obj, Dataset old, int user) {
    super(type, key, obj, old, user, Dataset.class);
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

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DatasetChanged)) return false;
    DatasetChanged that = (DatasetChanged) o;
    return Objects.equals(usernamesToInvalidate, that.usernamesToInvalidate);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(usernamesToInvalidate);
  }
}
