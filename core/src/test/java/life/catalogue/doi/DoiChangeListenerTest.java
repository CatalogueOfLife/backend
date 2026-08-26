package life.catalogue.doi;

import life.catalogue.api.event.DoiChange;
import life.catalogue.api.model.Agent;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSimple;
import life.catalogue.cache.LatestDatasetKeyCache;
import life.catalogue.db.mapper.DatasetArchiveMapper;
import life.catalogue.db.mapper.DatasetImportMapper;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.doi.datacite.model.DoiAttributes;
import life.catalogue.doi.service.DatasetConverter;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.doi.service.DoiExistsException;
import life.catalogue.doi.service.DoiService;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class DoiChangeListenerTest {
  static final int DATASET_KEY = 316146;
  static final int ATTEMPT = 1;

  @Test
  void freeStoreFile() throws IOException {
    var dir = new File("/tmp/DoiChangeListener");
    FileUtils.deleteDirectory(dir);
    var f1 = DoiChangeListener.freeStoreFile(dir);
    f1.createNewFile();
    var f2 = DoiChangeListener.freeStoreFile(dir);
    assertNotEquals(f1, f2);
    assertTrue(f1.exists());
    assertEquals(new File(dir, "event-1"), f1);
    assertFalse(f2.exists());
    assertEquals(new File(dir, "event-2"), f2);
    FileUtils.deleteDirectory(dir);
  }

  /**
   * A CREATE for a DOI that DataCite already knows must converge on the metadata we want instead of
   * failing. Two apps read the same broker queue during a deploy, so the loser of that race used to mail
   * a 422 "This DOI has already been taken" and give up - 422 is treated as permanent and never retried.
   */
  @Test
  void createExistingDoiUpdatesInstead() throws Exception {
    var dir = new File("/tmp/DoiChangeListenerExists");
    FileUtils.deleteDirectory(dir);
    var cfg = config(dir);
    var vdoi = cfg.datasetVersionDOI(DATASET_KEY, ATTEMPT);

    var doiService = mock(DoiService.class);
    doThrow(new DoiExistsException("already taken", vdoi)).when(doiService).create(any(DoiAttributes.class));

    var listener = listener(doiService, cfg);
    listener.start();
    try {
      listener.doiChanged(DoiChange.create(vdoi));

      var attrs = org.mockito.ArgumentCaptor.forClass(DoiAttributes.class);
      verify(doiService, timeout(10_000)).update(attrs.capture());
      assertEquals(vdoi, attrs.getValue().getDoi());
      // the dataset is public, so it must still be published despite the create having been a no-op
      verify(doiService, timeout(10_000)).publish(vdoi);
      // and above all: no failure mail
      verify(doiService, never()).notifyException(any(), any(), any());
    } finally {
      listener.stop();
      FileUtils.deleteDirectory(dir);
    }
  }

  /**
   * A stopped listener - an old app quiesced by /admin/component/stop-all during a blue-green deploy -
   * must not touch DataCite at all. This is what keeps two live apps from creating the same DOI twice.
   */
  @Test
  void stoppedListenerIgnoresEvents() throws Exception {
    var dir = new File("/tmp/DoiChangeListenerStopped");
    FileUtils.deleteDirectory(dir);
    var cfg = config(dir);
    var vdoi = cfg.datasetVersionDOI(DATASET_KEY, ATTEMPT);

    var doiService = mock(DoiService.class);
    var listener = listener(doiService, cfg);

    // never started
    assertFalse(listener.hasStarted());
    listener.doiChanged(DoiChange.create(vdoi));
    assertTrue(listener.list().isEmpty());

    // started and stopped again
    listener.start();
    assertTrue(listener.hasStarted());
    listener.stop();
    assertFalse(listener.hasStarted());
    listener.doiChanged(DoiChange.create(vdoi));
    assertTrue(listener.list().isEmpty());

    // give an erroneously submitted job a chance to run before we assert nothing happened
    Thread.sleep(500);
    verifyNoInteractions(doiService);
    FileUtils.deleteDirectory(dir);
  }

  private static DoiConfig config(File dir) {
    var cfg = new DoiConfig();
    cfg.prefix = DOI.TEST_PREFIX;
    cfg.store = dir.getAbsolutePath();
    cfg.waitPeriod = 2;
    return cfg;
  }

  private static DoiChangeListener listener(DoiService doiService, DoiConfig cfg) {
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

    var converter = new DatasetConverter(URI.create("https://www.catalogueoflife.org"), URI.create("https://www.checklistbank.org"), k -> null);
    return new DoiChangeListener(factory, doiService, mock(LatestDatasetKeyCache.class), converter, cfg);
  }
}
