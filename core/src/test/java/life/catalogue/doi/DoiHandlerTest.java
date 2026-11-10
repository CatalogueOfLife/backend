package life.catalogue.doi;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.OutboxType;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.db.mapper.DatasetArchiveMapper;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.doi.datacite.model.DoiAttributes;
import life.catalogue.doi.service.*;
import life.catalogue.outbox.DoiAction;

import java.net.URI;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class DoiHandlerTest {
  static final int DATASET_KEY = 316146;
  static final int ATTEMPT = 1;
  static final DoiConfig CFG = config();
  static final DOI VDOI = CFG.datasetVersionDOI(DATASET_KEY, ATTEMPT);

  static OutboxEntry entry(DOI doi, DoiAction... actions) {
    var e = new OutboxEntry();
    e.setId(1L);
    e.setType(OutboxType.DOI);
    e.setKey(doi.getDoiName());
    var params = ApiModule.MAPPER.createObjectNode();
    for (var a : actions) {
      params.put(a.name(), true);
    }
    e.setParams(params);
    e.setCreatedBy(100);
    return e;
  }

  /**
   * A create for a DOI that DataCite already knows must converge on the metadata we want instead of failing:
   * a rerun of a command that failed after the DOI was registered would otherwise mail a 422 and give up,
   * as 422 is treated as permanent.
   */
  @Test
  void createExistingDoiUpdatesInstead() throws Exception {
    var doiService = mock(DoiService.class);
    doThrow(new DoiExistsException("already taken", VDOI)).when(doiService).create(any(DoiAttributes.class));

    handler(doiService, false).handle(entry(VDOI, DoiAction.CREATE));

    var attrs = ArgumentCaptor.forClass(DoiAttributes.class);
    verify(doiService).update(attrs.capture());
    assertEquals(VDOI, attrs.getValue().getDoi());
    // the dataset is public, so it must still be published despite the create having been a no-op
    verify(doiService).publish(VDOI);
    verify(doiService, never()).notifyException(any(), any(), any());
  }

  /**
   * A create already sends the metadata and publishes the DOI of a public dataset,
   * so a merged update and publish do not call DataCite again.
   */
  @Test
  void createCoversUpdateAndPublish() throws Exception {
    var doiService = mock(DoiService.class);
    handler(doiService, false).handle(entry(VDOI, DoiAction.PUBLISH, DoiAction.UPDATE, DoiAction.CREATE));
    verify(doiService, times(1)).create(any(DoiAttributes.class));
    verify(doiService, never()).update(any(DoiAttributes.class));
    verify(doiService, times(1)).publish(VDOI);
  }

  /**
   * The metadata is updated before the DOI is published, so it becomes findable with current metadata.
   */
  @Test
  void updateBeforePublish() throws Exception {
    var doiService = mock(DoiService.class);
    handler(doiService, false).handle(entry(VDOI, DoiAction.PUBLISH, DoiAction.UPDATE));
    InOrder order = inOrder(doiService);
    order.verify(doiService).update(any(DoiAttributes.class));
    order.verify(doiService).publish(VDOI);
    verify(doiService, never()).create(any(DoiAttributes.class));
  }

  @Test
  void deleteRunsAlone() throws Exception {
    var doiService = mock(DoiService.class);
    when(doiService.delete(VDOI)).thenReturn(true);
    handler(doiService, false).handle(entry(VDOI, DoiAction.UPDATE, DoiAction.DELETE, DoiAction.PUBLISH));
    verify(doiService).delete(VDOI);
    verifyNoMoreInteractions(doiService);
  }

  @Test
  void otherPrefixIgnored() throws Exception {
    var doiService = mock(DoiService.class);
    handler(doiService, false).handle(entry(new DOI("10.5555", "d" + DATASET_KEY), DoiAction.CREATE));
    verifyNoInteractions(doiService);
  }

  /**
   * Only a started handler runs commands - an old app quiesced by /admin/component/stop-all during a blue-green
   * deploy leaves them waiting in the outbox. A handler of a server whose DoiUpdater is disabled drains them.
   */
  @Test
  void onlineOnlyWhenStarted() throws Exception {
    var h = handler(mock(DoiService.class), false);
    assertFalse(h.isOnline());
    h.start();
    assertTrue(h.hasStarted());
    assertTrue(h.isOnline());
    h.stop();
    assertFalse(h.isOnline());

    assertTrue(handler(mock(DoiService.class), true).isOnline());
  }

  @Test
  void permanentFailures() throws Exception {
    var h = handler(mock(DoiService.class), false);
    assertTrue(h.isPermanent(new DoiHttpException(422, VDOI)));
    assertTrue(h.isPermanent(new InvalidMetadataException("bad")));
    assertTrue(h.isPermanent(new IllegalArgumentException("Source dataset DOIs not supported any longer")));
    assertFalse(h.isPermanent(new DoiHttpException(500, VDOI)));
    assertFalse(h.isPermanent(new DoiException(VDOI, "timeout")));
    assertEquals(4, h.retryDelay(2).toHours());
  }

  @Test
  void failureIsMailed() throws Exception {
    var doiService = mock(DoiService.class);
    var ex = new DoiHttpException(422, VDOI);
    handler(doiService, false).onFailed(entry(VDOI, DoiAction.CREATE), ex);
    verify(doiService).notifyException(eq(VDOI), any(), eq(ex));
  }

  private static DoiConfig config() {
    var cfg = new DoiConfig();
    cfg.prefix = DOI.TEST_PREFIX;
    cfg.waitPeriod = 2;
    return cfg;
  }

  private static DoiHandler handler(DoiService doiService, boolean alwaysOnline) {
    var d = new Dataset();
    d.setKey(DATASET_KEY);
    d.setAttempt(ATTEMPT);
    d.setTitle("Ten new species of Neotrichia");
    d.setCreator(List.of(Agent.person("Jane", "Doe")));

    var dm = mock(DatasetMapper.class);
    when(dm.getSimple(anyInt())).thenReturn(new DatasetSimple());
    when(dm.get(anyInt())).thenReturn(d);
    when(dm.isPrivate(anyInt())).thenReturn(false);

    var session = mock(SqlSession.class);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);
    when(session.getMapper(DatasetArchiveMapper.class)).thenReturn(mock(DatasetArchiveMapper.class));
    when(session.getMapper(DatasetImportMapper.class)).thenReturn(mock(DatasetImportMapper.class));

    var factory = mock(SqlSessionFactory.class);
    when(factory.openSession()).thenReturn(session);
    when(factory.openSession(anyBoolean())).thenReturn(session);

    var converter = new DatasetConverter(URI.create("https://www.catalogueoflife.org"),
      URI.create("https://www.checklistbank.org"), k -> null);
    return new DoiHandler(factory, doiService, mock(LatestDatasetKeyCache.class), converter, CFG, alwaysOnline);
  }
}
