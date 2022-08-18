package life.catalogue.dao;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DecisionMapper;

import org.gbif.nameparser.api.Rank;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.time.StopWatch;
import org.apache.ibatis.session.SqlSession;
import org.junit.*;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import com.google.common.collect.Sets;

import static org.junit.Assert.*;

public class DuplicateDaoTest {
  final static int datasetKey = 1000;
  DuplicateDao dao;
  StopWatch watch = new StopWatch();

  public static PgSetupRule pg = new PgSetupRule();
  public static final TestDataRule dataRule = TestDataRule.duplicates();

  @ClassRule
  public final static TestRule chain = RuleChain
    .outerRule(pg)
    .around(dataRule);

  @BeforeClass
  public static void setup() throws Exception {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      final DecisionMapper dm = session.getMapper(DecisionMapper.class);
      create(dm, "15", Rank.SPECIES);
      create(dm, "28", Rank.SUBSPECIES);
      session.commit();
    }
  }

  private static void create(DecisionMapper dm, String id, Rank rank) {
    EditorialDecision d = new EditorialDecision();
    d.setDatasetKey(Datasets.COL);
    d.setSubjectDatasetKey(datasetKey);
    d.setSubject(SimpleNameLink.of(id, "Nana", rank));
    d.setMode(EditorialDecision.Mode.BLOCK);
    d.applyUser(TestEntityGenerator.USER_EDITOR);
    dm.create(d);
  }

  @Before
  public void init() {
    dao = new DuplicateDao(PgSetupRule.getSqlSessionFactory());
    watch.reset();
  }

  @Test(expected = IllegalArgumentException.class)
  public void duplicatesIAE() {
    // no catalogue/project given but filtering decisions
    var req = new DuplicateDao.DuplicateRequest(EntityType.NAME_USAGE, MatchingMode.STRICT, null, null, datasetKey, null, null, null, null, null, null, null, null, null, true, null);
    dao.page(req, null);
  }

  @Test
  public void duplicates() {
    Set<TaxonomicStatus> status = new HashSet<>();
    int minSize = 2;
    List<Duplicate> dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, null, null, null, null, null, new Page(0, 10));
    assertComplete(10, dups, minSize);


    Page p = new Page(0, 100);
    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, null, status, null, null, null, p);
    show(dups);
    assertComplete(21, dups, minSize);

    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, null, status, null, null, true, p);
    show(dups);
    assertComplete(2, dups, minSize);

    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, null, status, null, null, false, p);
    show(dups);
    assertComplete(19, dups, minSize);

    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, NameCategory.UNINOMIAL, null, status, null, null, null, p);
    assertComplete(0, dups, minSize);

    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, NameCategory.BINOMIAL, null, status, null, null, null, p);
    assertComplete(17, dups, minSize);

    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, NameCategory.TRINOMIAL, null, status, null, null, null, p);
    assertComplete(4, dups, minSize);


    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, ranks(Rank.SUBSPECIES), status, null, null, null, p);
    assertComplete(4, dups, minSize);

    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, ranks(Rank.SUBSPECIES), status, true, null, null, p);
    assertComplete(2, dups, minSize);

    status.add(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, ranks(Rank.SUBSPECIES), status, true, null, null, p);
    assertComplete(2, dups, minSize);

    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, null, status, true, null, null, p);
    assertComplete(6, dups, minSize);

    status.add(TaxonomicStatus.SYNONYM);
    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, null, status, true, null, null, p);
    assertComplete(10, dups, minSize);

    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, null, status, false, null, null, p);
    assertComplete(0, dups, minSize);

    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, null, null, false, null, null, p);
    assertComplete(5, dups, minSize);

    dups = find(MatchingMode.STRICT, 3, datasetKey, null, null, null, status, true, null, null, p);
    assertComplete(2, dups, 3);

    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, null, status, true, true, null, p);
    assertComplete(9, dups, minSize);

    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, null, status, null, true, null, p);
    assertComplete(9, dups, minSize);

    dups = find(MatchingMode.STRICT, minSize, datasetKey, null, null, null, status, null, false, null, p);
    assertComplete(1, dups, minSize);


    // FUZZY mode

    status.clear();
    dups = find(MatchingMode.FUZZY, minSize, datasetKey, null, null, null, status, null, null, null, p);
    assertComplete(25, dups, minSize);

    dups = find(MatchingMode.FUZZY, minSize, datasetKey, null, null, null, status, true, null, null, p);
    assertComplete(4, dups, minSize);

    dups = find(MatchingMode.FUZZY, minSize, datasetKey, null, null, ranks(Rank.SUBSPECIES), status, null, null, null, p);
    assertComplete(5, dups, minSize);

    dups = find(MatchingMode.FUZZY, minSize, datasetKey, null, null, ranks(Rank.SPECIES, Rank.SUBSPECIES), status, null, null, null, p);
    assertComplete(24, dups, minSize);

    dups = find(MatchingMode.FUZZY, minSize, datasetKey, null, null, ranks(Rank.SUBSPECIES), status, false, null, null, p);
    assertComplete(4, dups, minSize);

    dups = find(MatchingMode.FUZZY, minSize, datasetKey, null, null, ranks(Rank.SUBSPECIES), status, true, null, null, p);
    assertComplete(1, dups, minSize);

    dups = find(MatchingMode.FUZZY, minSize, datasetKey, 999, null, null, status, null, null, null, p);
    assertComplete(0, dups, minSize);

    System.out.println(watch);
  }

  @Test
  public void duplicateNames() {
    int minSize = 2;
    List<Duplicate> dups = findNames(MatchingMode.STRICT, minSize, datasetKey, null, null, null, new Page(0, 10));
    assertCompleteBareName(10, dups, minSize);

    Page p = new Page(0, 100);

    dups = findNames(MatchingMode.STRICT, minSize, datasetKey, NameCategory.BINOMIAL, null, null, p);
    show(dups);
    assertCompleteBareName(17, dups, minSize);

    dups = findNames(MatchingMode.STRICT, minSize, datasetKey, null, ranks(Rank.SUBSPECIES), null, p);
    show(dups);
    assertCompleteBareName(4, dups, minSize);


    // FUZZY mode
    dups = findNames(MatchingMode.FUZZY, minSize, datasetKey, null, null, null, p);
    show(dups);
    assertCompleteBareName(25, dups, minSize);

    dups = findNames(MatchingMode.FUZZY, minSize, datasetKey, null, ranks(Rank.SPECIES, Rank.SUBSPECIES), null, p);
    show(dups);
    assertCompleteBareName(24, dups, minSize);

    dups = findNames(MatchingMode.FUZZY, minSize, datasetKey, null, ranks(Rank.SPECIES, Rank.SUBSPECIES), true, p);
    show(dups);
    assertCompleteBareName(4, dups, minSize);

    System.out.println(watch);
  }

  private static Set<Rank> ranks(Rank... rank) {
    if (rank == null) {
      return Collections.EMPTY_SET;
    }
    return Sets.newHashSet(rank);
  }

  private List<Duplicate> find(MatchingMode mode, Integer minSize, int datasetKey, Integer sourceDatasetKey, NameCategory category, Set<Rank> ranks, Set<TaxonomicStatus> status, Boolean authorshipDifferent, Boolean acceptedDifferent, Boolean withDecision, Page page) {
    if (!watch.isStarted()) {
      watch.start();
    } else {
      watch.resume();
    }
    var req = new DuplicateDao.DuplicateRequest(EntityType.NAME_USAGE, mode, null, minSize, datasetKey, sourceDatasetKey, null, category, ranks, status,
      authorshipDifferent, acceptedDifferent, null, null, withDecision, Datasets.COL);
    ResultPage<Duplicate> result = dao.page(req, page);
    watch.suspend();
    return result.getResult();
  }

  private List<Duplicate> findNames(MatchingMode mode, Integer minSize, int datasetKey, NameCategory category, Set<Rank> ranks, Boolean authorshipDifferent, Page page) {
    if (!watch.isStarted()) {
      watch.start();
    } else {
      watch.resume();
    }

    var req = new DuplicateDao.DuplicateRequest(EntityType.NAME, mode, null, minSize, datasetKey, null, null, category, ranks,
      null, authorshipDifferent, null, null, null, null, Datasets.COL);
    ResultPage<Duplicate> result = dao.page(req, page);
    watch.suspend();
    return result.getResult();
  }

  private static void assertComplete(int expectedSize, List<Duplicate> dups, int minSize) {
    assertEquals(expectedSize, dups.size());
    for (Duplicate d : dups) {
      assertTrue(d.getUsages().size() >= minSize);
      for (Duplicate.UsageDecision u : d.getUsages()) {
        assertNotNull(u.getUsage().getId());
        assertNotNull(u.getUsage().getName());
        assertNotNull(u.getUsage().getName().getScientificName());
        assertNotNull(u.getUsage().getName().getId());
        if (u.getUsage().isSynonym()) {
          Synonym s = (Synonym) u.getUsage();
          assertNotNull(s.getAccepted());
          assertNotNull(s.getAccepted().getName());
          assertEquals(s.getAccepted().getId(), ((Synonym) u.getUsage()).getParentId());
        }
      }
    }
  }

  private static void assertCompleteBareName(int expectedSize, List<Duplicate> dups, int minSize) {
    assertEquals(expectedSize, dups.size());
    for (Duplicate d : dups) {
      assertTrue(d.getUsages().size() >= minSize);
      for (Duplicate.UsageDecision u : d.getUsages()) {
        assertNull(u.getUsage().getId());
        assertNotNull(u.getUsage().getName());
        assertNotNull(u.getUsage().getName().getScientificName());
        assertNotNull(u.getUsage().getName().getId());
        assertTrue(u.getUsage().isBareName());
      }
    }
  }

  private static void show(List<Duplicate> dups) {
    System.out.println("---  ---  ---  ---");
    int idx = 1;
    for (Duplicate d : dups) {
      System.out.println("\n#" + idx++ + "  " + d.getKey() + " ---");
      for (Duplicate.UsageDecision u : d.getUsages()) {
        System.out.print(" " + u.getUsage().getId() + "  " + u.getUsage().getName().getLabel() + "  " + u.getUsage().getStatus());
        System.out.print(" -- " + u.getUsage().getName().getAuthorshipNormalized() + " -- ");
        if (u.getUsage().isSynonym()) {
          Synonym s = (Synonym) u.getUsage();
          System.out.println(", pid="+s.getParentId() + ", acc="+s.getAccepted().getName().getScientificName());
        } else if (u.getUsage().isTaxon()){
          Taxon t = (Taxon) u.getUsage();
          System.out.println(", pid="+t.getParentId());
        }
      }
    }
  }
}