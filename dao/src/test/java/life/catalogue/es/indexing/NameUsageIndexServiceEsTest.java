package life.catalogue.es.indexing;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.common.io.TempFile;
import life.catalogue.config.EsConfig;
import life.catalogue.config.IndexConfig;
import life.catalogue.es.EsSetupRule;
import life.catalogue.es.EsTestBase;

import life.catalogue.es.search.NameUsageSearchServiceEs;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.TestDataRule;

import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.postgresql.core.BaseConnection;
import org.postgresql.core.TransactionState;

import static org.junit.Assert.*;

public class NameUsageIndexServiceEsTest {

  static PgSetupRule pgSetupRule = new PgSetupRule();
  static TestDataRule testDataRule = TestDataRule.draftWithSectors();
  static EsSetupRule esSetup = new EsSetupRule();

  @ClassRule
  public final static TestRule classRules = RuleChain
    .outerRule(pgSetupRule)
    .around(testDataRule)
    .around(esSetup);

  NameUsageIndexServiceEs service;
  NameUsageSearchServiceEs search;
  TempFile dir;
  final int datasetKey = 3;

  @Before
  public void init() throws IOException {
    dir = TempFile.directory();
    var client = esSetup.getClient();
    var cfg = esSetup.getEsConfig();
    service = new NameUsageIndexServiceEs(client, cfg, dir.file, PgSetupRule.getSqlSessionFactory());
    search = new NameUsageSearchServiceEs(cfg.index.name, client);
    // create empty index
    service.createEmptyIndex();
  }

  @After
  public void tearDown() throws IOException {
    dir.close();
  }

  @Test
  public void indexAndDeleteDataset() {
    int num = 0;
    for (int i=0; i<5; i++) {
      service.indexDataset(datasetKey);
      if (num > 0) {
        assertEquals(num, search.count(datasetKey));
      } else {
        num = search.count(datasetKey);
      }
    }
  }

  @Test
  public void indexDataset() {
    service.indexDataset(datasetKey);
  }

  @Test
  public void deleteDataset() {
    service.deleteDataset(datasetKey);
  }

  @Test
  public void indexSector() {
    service.indexSector(DSID.of(datasetKey, 100));
  }

  /**
   * Indexing a sector must not keep a transaction open while it indexes the usages on other sessions.
   * For a large sector like Mollusca that takes longer than the idle-in-transaction timeout of the prod server,
   * which then killed the connection the bare names were read from afterwards, see #1690.
   */
  @Test
  public void indexSectorHoldsNoTransaction() {
    var tracker = new SessionTracker(PgSetupRule.getSqlSessionFactory());
    var tracked = new NameUsageIndexServiceEs(esSetup.getClient(), esSetup.getEsConfig(), dir.file, tracker.factory);
    var stats = tracked.indexSector(DSID.of(datasetKey, 100));
    assertTrue(stats.usages > 0);
    assertTrue(tracker.violations.toString(), tracker.violations.isEmpty());
  }

  /**
   * Wraps a session factory and records every session that is still inside a transaction
   * at the moment another session gets opened.
   */
  static class SessionTracker {
    final List<SqlSession> open = new ArrayList<>();
    final List<String> violations = new ArrayList<>();
    final SqlSessionFactory factory;

    SessionTracker(SqlSessionFactory delegate) {
      factory = (SqlSessionFactory) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{SqlSessionFactory.class},
        (proxy, method, args) -> {
          Object result = invoke(method, delegate, args);
          if (result instanceof SqlSession session) {
            check();
            return track(session);
          }
          return result;
        });
    }

    private static Object invoke(java.lang.reflect.Method method, Object target, Object[] args) throws Throwable {
      try {
        return method.invoke(target, args);
      } catch (InvocationTargetException e) {
        throw e.getCause();
      }
    }

    private synchronized void check() throws SQLException {
      for (SqlSession s : open) {
        var state = s.getConnection().unwrap(BaseConnection.class).getTransactionState();
        if (state != TransactionState.IDLE) {
          violations.add("A session is " + state + " while another one is opened");
        }
      }
    }

    private synchronized SqlSession track(SqlSession session) {
      SqlSession wrapped = (SqlSession) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{SqlSession.class},
        (proxy, method, args) -> {
          if (method.getName().equals("close")) {
            synchronized (this) {
              open.removeIf(s -> s == proxy);
            }
          }
          return invoke(method, session, args);
        });
      open.add(wrapped);
      return wrapped;
    }
  }

  @Test
  public void deleteSector() {
    service.deleteSector(DSID.of(datasetKey, 100));
  }

  @Test
  public void createEmptyIndex() {
    service.createEmptyIndex();
  }
}