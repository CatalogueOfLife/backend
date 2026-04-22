package life.catalogue.matching;

import life.catalogue.api.model.Dataset;
import life.catalogue.config.MatchingConfig;
import life.catalogue.concurrent.JobExecutor;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.metadata.coldp.DatasetJsonWriter;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class UsageMatcherFactoryTest {

  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  @Mock SqlSessionFactory sqlSessionFactory;
  @Mock NameIndex nameIndex;
  @Mock JobExecutor executor;

  private UsageMatcherFactory factory() {
    MatchingConfig cfg = new MatchingConfig();
    cfg.storageDir = tmp.getRoot();
    cfg.chronicle = true;
    return new UsageMatcherFactory(cfg, nameIndex, sqlSessionFactory, executor);
  }

  @Test
  public void matcherExistsReturnsFalseWhenNeitherInMemoryNorOnDisk() {
    var f = factory();
    assertFalse(f.matcherExists(999));
  }

  @Test
  public void matcherExistsReturnsTrueWhenDirectoryOnDisk() {
    var f = factory();
    new File(tmp.getRoot(), "999").mkdirs();
    assertTrue(f.matcherExists(999));
  }

  @Test
  public void cleanupRemovesStaleMatcher() throws Exception {
    var f = factory();
    int key = 42;

    // create a fake matcher dir and sidecar with attempt=5
    File matcherDir = new File(tmp.getRoot(), String.valueOf(key));
    matcherDir.mkdirs();
    Dataset stored = new Dataset();
    stored.setKey(key);
    stored.setAttempt(5);
    DatasetJsonWriter.write(stored, new File(tmp.getRoot(), key + ".json"));

    // mock DB returning attempt=7
    Dataset current = new Dataset();
    current.setKey(key);
    current.setAttempt(7);
    SqlSession session = mock(SqlSession.class);
    DatasetMapper dm = mock(DatasetMapper.class);
    when(sqlSessionFactory.openSession()).thenReturn(session);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);
    when(dm.get(key)).thenReturn(current);

    int removed = f.cleanup();

    assertEquals(1, removed);
    assertFalse("matcher dir should be deleted", matcherDir.exists());
    assertFalse("sidecar should be deleted", new File(tmp.getRoot(), key + ".json").exists());
  }

  @Test
  public void cleanupKeepsFreshMatcher() throws Exception {
    var f = factory();
    int key = 43;

    File matcherDir = new File(tmp.getRoot(), String.valueOf(key));
    matcherDir.mkdirs();
    Dataset stored = new Dataset();
    stored.setKey(key);
    stored.setAttempt(9);
    DatasetJsonWriter.write(stored, new File(tmp.getRoot(), key + ".json"));

    Dataset current = new Dataset();
    current.setKey(key);
    current.setAttempt(9);
    SqlSession session = mock(SqlSession.class);
    DatasetMapper dm = mock(DatasetMapper.class);
    when(sqlSessionFactory.openSession()).thenReturn(session);
    when(session.getMapper(DatasetMapper.class)).thenReturn(dm);
    when(dm.get(key)).thenReturn(current);

    int removed = f.cleanup();

    assertEquals(0, removed);
    assertTrue("matcher dir should remain", matcherDir.exists());
  }
}
