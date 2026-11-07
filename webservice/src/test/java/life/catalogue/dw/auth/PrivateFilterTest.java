package life.catalogue.dw.auth;

import life.catalogue.db.mapper.DatasetMapper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PrivateFilterTest {

  /**
   * A dataset made private while a request still reads its old public state from the db must stay cached as private.
   * The cache used to be a plain map filled with get, db read, put - the stale read overwrote the change event.
   */
  @Test
  public void changeDuringDbRead() throws Exception {
    final int key = 1001;
    final CountDownLatch inDb = new CountDownLatch(1);
    final CountDownLatch release = new CountDownLatch(1);

    var dm = mock(DatasetMapper.class);
    when(dm.isPrivate(key)).thenAnswer(inv -> {
      inDb.countDown();
      release.await(10, TimeUnit.SECONDS);
      return false; // what the db said before the dataset was made private
    });
    var session = mock(SqlSession.class);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);
    var factory = mock(SqlSessionFactory.class);
    when(factory.openSession()).thenReturn(session);

    var filter = new PrivateFilter();
    filter.setSqlSessionFactory(factory);

    var request = new Thread(() -> filter.isPrivate(key));
    request.start();
    assertTrue(inDb.await(10, TimeUnit.SECONDS));

    // the dataset is made private while the request is reading the db
    var event = new Thread(() -> filter.updateCache(key, true));
    event.start();
    // give the event a chance to run its update before the db read returns
    event.join(200);
    release.countDown();
    request.join(10_000);
    event.join(10_000);

    assertTrue("a dataset made private must not be cached as public", filter.isPrivate(key));
  }
}
