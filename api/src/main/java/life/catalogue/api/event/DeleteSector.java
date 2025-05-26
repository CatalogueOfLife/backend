package life.catalogue.api.event;

import life.catalogue.api.model.DSID;

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
}
