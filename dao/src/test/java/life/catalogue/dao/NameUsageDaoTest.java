package life.catalogue.dao;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.DSIDValue;
import life.catalogue.api.model.SimpleNameInDataset;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.es.indexing.NameUsageIndexService;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the two phase related usage lookup.
 *
 * The apple test data links root-2 (name-2) and s1 (name-3) to the same names index entry 3,
 * so the related usages of root-2 are exactly [s1].
 */
public class NameUsageDaoTest extends DaoTestBase {

  static final int datasetKey = TestDataRule.APPLE.key;

  NameUsageDao dao() {
    return new NameUsageDao(factory(), NameUsageIndexService.passThru());
  }

  @Test
  public void related() throws Exception {
    var results = dao().related(datasetKey, "root-2", false, null, null, null, null, null);
    assertEquals(1, results.size());
    assertEquals("s1", results.getFirst().getId());

    // only datasets with a gbif key, apple has none
    assertTrue(dao().related(datasetKey, "root-2", true, null, null, null, null, null).isEmpty());

    // restricted to the apple dataset itself
    results = dao().related(datasetKey, "root-2", false, null, null, null, List.of(datasetKey), null);
    assertEquals(1, results.size());
    assertEquals("s1", results.getFirst().getId());

    // apple is a PROJECT of type OTHER, so an origin filter for PROJECT keeps it
    results = dao().related(datasetKey, "root-2", false, null, Set.of(DatasetOrigin.PROJECT), null, null, null);
    assertEquals(1, results.size());
    assertEquals("s1", results.getFirst().getId());

    // filters that exclude apple
    assertTrue(dao().related(datasetKey, "root-2", false, null, null, null, List.of(1, 2, 3), null).isEmpty());
    assertTrue(dao().related(datasetKey, "root-2", false, null, null, null, null, List.of(UUID.randomUUID())).isEmpty());
    assertTrue(dao().related(datasetKey, "root-2", false, null, Set.of(DatasetOrigin.EXTERNAL), null, null, null).isEmpty());
    assertTrue(dao().related(datasetKey, "root-2", false, null, null,
      List.of(DatasetType.ARTICLE, DatasetType.NOMENCLATURAL), null, null).isEmpty());
  }

  /**
   * The origin usage matches its own name, so it comes back from phase 2 and must be filtered out.
   */
  @Test
  public void relatedExcludesOriginUsage() throws Exception {
    List<SimpleNameInDataset> results = dao().related(datasetKey, "root-2", false, null, null, null, null, null);
    assertTrue(results.stream().noneMatch(u -> u.getDatasetKey() == datasetKey && u.getId().equals("root-2")));
  }

  /**
   * Phase 1 resolves the names index matches down to name ids only. It returns one entry per matching name,
   * including the origin's own name - the origin usage is only dropped after phase 2.
   */
  @Test
  public void listRelatedNames() throws Exception {
    var names = mapper(NameUsageMapper.class).listRelatedNames(DSID.of(datasetKey, "root-2"),
      false, null, null, null, null, null, 300);
    assertEquals(Set.of("name-2", "name-3"), names.stream().map(DSID::getId).collect(Collectors.toSet()));
    names.forEach(n -> assertEquals(datasetKey, (int) n.getDatasetKey()));
  }

  @Test
  public void listRelatedUsages() throws Exception {
    var usages = mapper(NameUsageMapper.class)
      .listRelatedUsages(List.of(datasetKey), List.of(new DSIDValue<>(datasetKey, "name-3")));
    assertEquals(1, usages.size());
    assertEquals("s1", usages.getFirst().getId());

    // name ids are only unique within a dataset, so a pair must never match another dataset's name of the same id
    assertTrue(mapper(NameUsageMapper.class)
      .listRelatedUsages(List.of(datasetKey + 1), List.of(new DSIDValue<>(datasetKey + 1, "name-3"))).isEmpty());
  }

  /**
   * The whole reason listRelated was split: phase 2 must prune to the partitions of the datasets it asks for.
   * That only holds if the dataset_key predicate is repeated on BOTH partitioned tables - postgres propagates a
   * constant "=" across the join equality n.dataset_key=u.dataset_key, but NOT an IN list of several keys.
   * Filtering name_usage alone leaves every name partition locked.
   *
   * Uses two dataset keys that hash into the same name partition, so a correctly pruned query touches exactly one
   * partition of each table whatever the partition count is, while the unpruned one touches all of them. Asking for
   * datasets that hold no data is fine - partition pruning does not depend on the rows.
   *
   * Runs on its own session so the locks taken while loading the test data do not pollute the count.
   */
  @Test
  public void listRelatedUsagesPrunesPartitions() throws Exception {
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession(false)) {
      int sibling = datasetKeySharingPartition(s, datasetKey);
      s.getMapper(NameUsageMapper.class).listRelatedUsages(List.of(datasetKey, sibling),
        List.of(new DSIDValue<>(datasetKey, "name-3"), new DSIDValue<>(sibling, "whatever")));

      assertEquals("name_usage must be pruned to the single shared partition",
        1, lockedPartitions(s, "name_usage"));
      assertEquals("name must be pruned just like name_usage - is the dataset_key predicate repeated on n?",
        1, lockedPartitions(s, "name"));
    }
  }

  /**
   * @return another dataset key that hashes into the same partition of the name table as the given one
   */
  private static int datasetKeySharingPartition(SqlSession session, int datasetKey) throws Exception {
    var sql = "WITH m AS (SELECT count(*)::int AS n FROM pg_inherits i"
              + "            JOIN pg_class p ON p.oid=i.inhparent WHERE p.relname='name'),"
              + "     r AS (SELECT rr FROM m, generate_series(0, m.n-1) rr"
              + "            WHERE satisfies_hash_partition('name'::regclass, m.n, rr, " + datasetKey + "))"
              + " SELECT g FROM generate_series(" + (datasetKey + 1) + ", " + (datasetKey + 5000) + ") g, m, r"
              + " WHERE satisfies_hash_partition('name'::regclass, m.n, r.rr, g) LIMIT 1";
    try (Statement st = session.getConnection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
      assertTrue("no dataset key sharing a partition with " + datasetKey + " found", rs.next());
      return rs.getInt(1);
    }
  }

  /**
   * @return number of locked base table partitions of the given partitioned table in this session
   */
  private static int lockedPartitions(SqlSession session, String table) throws Exception {
    var sql = "SELECT count(*) FROM pg_locks l JOIN pg_class c ON c.oid=l.relation"
              + " WHERE l.pid=pg_backend_pid() AND l.locktype='relation' AND c.relkind='r'"
              + " AND c.relname ~ '^" + table + "_mod[0-9]+$'";
    try (Statement st = session.getConnection().createStatement(); ResultSet rs = st.executeQuery(sql)) {
      rs.next();
      return rs.getInt(1);
    }
  }
}
