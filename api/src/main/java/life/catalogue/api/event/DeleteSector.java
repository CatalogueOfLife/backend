package life.catalogue.api.event;

import life.catalogue.api.model.DSID;

import java.util.Objects;

/**
 * post this message in case a sector should be scheduled for deletion.
 */
public class DeleteSector implements Event {
  public DSID<Integer> key;
  public int user;

  public DeleteSector() {
  }

  public DeleteSector(DSID<Integer> key, int user) {
    this.key = key;
    this.user = user;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof DeleteSector)) return false;
    DeleteSector that = (DeleteSector) o;
    return user == that.user && Objects.equals(key, that.key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, user);
  }
}
