package life.catalogue.api.event;

import life.catalogue.api.model.DSID;

/**
 * post this message in case a sector should be schedules for deletion.
 */
public class DeleteSector {
  public final DSID<Integer> key;

  public DeleteSector(DSID<Integer> key) {
    this.key = key;
  }
}
