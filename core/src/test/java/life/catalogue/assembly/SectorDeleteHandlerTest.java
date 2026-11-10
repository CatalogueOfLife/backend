package life.catalogue.assembly;

import life.catalogue.api.exception.NotFoundException;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.OutboxEntry;
import life.catalogue.api.vocab.OutboxType;
import life.catalogue.outbox.Outbox;
import life.catalogue.outbox.RetryLaterException;

import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class SectorDeleteHandlerTest {
  static final DSID<Integer> KEY = DSID.of(3, 17);

  static OutboxEntry entry() {
    var e = new OutboxEntry();
    e.setId(1L);
    e.setType(OutboxType.SECTOR_DELETE);
    e.setKey(Outbox.sectorKey(KEY));
    e.setCreatedBy(100);
    return e;
  }

  @Test
  public void queued() throws Exception {
    var sm = mock(SyncManager.class);
    when(sm.deleteSector(KEY, true, 100)).thenReturn(true);
    new SectorDeleteHandler(sm).handle(entry());
    verify(sm).deleteSector(KEY, true, 100);
  }

  @Test(expected = RetryLaterException.class)
  public void busy() throws Exception {
    var sm = mock(SyncManager.class);
    when(sm.isQueuedOrRunning(KEY)).thenReturn(true);
    new SectorDeleteHandler(sm).handle(entry());
  }

  /**
   * A sector or project that no longer exists is reported as not found and completes the command.
   */
  @Test
  public void gone() throws Exception {
    var sm = mock(SyncManager.class);
    when(sm.deleteSector(any(), anyBoolean(), anyInt())).thenThrow(new NotFoundException("Sector 3:17 does not exist"));
    new SectorDeleteHandler(sm).handle(entry());
  }

  /**
   * A sync queued in between the busy check and the deletion rejects it, which must not complete the command.
   */
  @Test(expected = RetryLaterException.class)
  public void rejectedRetriesLater() throws Exception {
    var sm = mock(SyncManager.class);
    when(sm.deleteSector(KEY, true, 100)).thenReturn(false);
    new SectorDeleteHandler(sm).handle(entry());
  }

  /**
   * Any other illegal argument is not a missing sector and fails the command, so it stays visible.
   */
  @Test(expected = IllegalArgumentException.class)
  public void illegalArgumentFails() throws Exception {
    var sm = mock(SyncManager.class);
    when(sm.deleteSector(KEY, true, 100)).thenThrow(new IllegalArgumentException("No user 100 existing"));
    new SectorDeleteHandler(sm).handle(entry());
  }
}
