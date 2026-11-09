package life.catalogue.release;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.OutboxEntry;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.OutboxType;
import life.catalogue.concurrent.BackgroundJob;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.config.ReleaseConfig;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.doi.service.DoiConfig;
import life.catalogue.outbox.RetryLaterException;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class ReleasePublishedHandlerTest {
  static final int KEY = 3001;

  static OutboxEntry entry() {
    var e = new OutboxEntry();
    e.setId(7L);
    e.setType(OutboxType.RELEASE_PUBLISHED);
    e.setKey(String.valueOf(KEY));
    e.setParams(ApiModule.MAPPER.createObjectNode().put("releaseKey", KEY));
    e.setCreatedBy(101);
    return e;
  }

  static Dataset release(boolean privat) {
    var d = new Dataset();
    d.setKey(KEY);
    d.setSourceKey(1000);
    d.setOrigin(DatasetOrigin.RELEASE);
    d.setPrivat(privat);
    return d;
  }

  static ReleasePublishedHandler handler(Dataset d, JobExecutor executor) {
    var dm = mock(DatasetMapper.class);
    when(dm.get(KEY)).thenReturn(d);
    var session = mock(SqlSession.class);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);
    var factory = mock(SqlSessionFactory.class);
    when(factory.openSession()).thenReturn(session);
    when(factory.openSession(anyBoolean())).thenReturn(session);
    return new ReleasePublishedHandler(new ReleaseConfig(), null, new DoiConfig(), factory, null, null, executor);
  }

  @Test
  void submitsPublishJob() {
    var executor = mock(JobExecutor.class);
    when(executor.hasStarted()).thenReturn(true);
    handler(release(false), executor).handle(entry());
    var job = ArgumentCaptor.forClass(BackgroundJob.class);
    verify(executor).submit(job.capture());
    assertInstanceOf(PublishReleaseJob.class, job.getValue());
    assertEquals(KEY, job.getValue().datasetKey());
    assertEquals(101, job.getValue().getUserKey());
  }

  @Test
  void waitsForExecutor() {
    var executor = mock(JobExecutor.class);
    when(executor.hasStarted()).thenReturn(false);
    assertThrows(RetryLaterException.class, () -> handler(release(false), executor).handle(entry()));
    verify(executor, never()).submit(any());
  }

  @Test
  void skipsWhatIsNoPublicRelease() {
    var executor = mock(JobExecutor.class);
    when(executor.hasStarted()).thenReturn(true);
    // made private again
    handler(release(true), executor).handle(entry());
    // gone
    handler(null, executor).handle(entry());
    // deleted
    var deleted = release(false);
    deleted.setDeleted(java.time.LocalDateTime.now());
    handler(deleted, executor).handle(entry());
    verify(executor, never()).submit(any());
  }
}
