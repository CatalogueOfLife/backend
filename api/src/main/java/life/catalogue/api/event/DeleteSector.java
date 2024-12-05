package life.catalogue.api.event;

import life.catalogue.api.model.DSID;

/**
 * post this message in case a sector should be schedules for deletion.
 */
public class DeleteSector {
  public final DSID<Integer> key;
  public final int user;

  public DeleteSector(DSID<Integer> key, int user) {
    this.key = key;
    this.user = user;
  }
}
