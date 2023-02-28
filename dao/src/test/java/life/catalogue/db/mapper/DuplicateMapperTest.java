package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Duplicate;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.model.Page;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.MatchingMode;
import life.catalogue.api.vocab.NameCategory;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.TestDataRule;

import org.gbif.nameparser.api.Rank;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.ibatis.session.SqlSession;
import org.javers.common.collections.Lists;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import com.google.common.collect.Sets;

import static org.junit.Assert.*;

public class DuplicateMapperTest {
  
  final static int datasetKey = 1000;
  DuplicateMapper mapper;
  SqlSession session;

  public static PgSetupRule pg = new PgSetupRule();
  public static final TestDataRule dataRule = TestDataRule.duplicates();

  @ClassRule
  public final static TestRule chain = RuleChain
    .outerRule(pg)
    .around(dataRule);

  @Before
  public void init() {
    session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true);
    mapper = session.getMapper(DuplicateMapper.class);
  }
  
  @After
  public void destroy() {
    session.close();
  }
  
  @Test
  public void usagesWithDecisions() {
    List<String> ids = Lists.immutableListOf("55", "46");
    List<Duplicate.UsageDecision> res = mapper.usagesByIds(datasetKey, Datasets.COL, ids);
    assertEquals(2, res.size());
    for (Duplicate.UsageDecision u : res) {
      assertFalse(u.getClassification().isEmpty());
      assertNull(u.getDecision());
    }
  
    // now with a decision
    DecisionMapper dm = session.getMapper(DecisionMapper.class);
  
    EditorialDecision d1 = TestEntityGenerator.setUser(new EditorialDecision());
    d1.setDatasetKey(Datasets.COL);
    d1.setSubjectDatasetKey(datasetKey);
    d1.setSubject(TreeMapperTest.nameref("45"));
    d1.setMode(EditorialDecision.Mode.UPDATE);
    dm.create(d1);
    
    res = mapper.usagesByIds(datasetKey, Datasets.COL, ids);
    assertEquals(2, res.size());
    for (Duplicate.UsageDecision u : res) {
      assertFalse(u.getClassification().isEmpty());
    }

    ids = Lists.immutableListOf("45");
    res = mapper.usagesByIds(datasetKey, Datasets.COL, ids);
    EditorialDecision d = res.get(0).getDecision();
    assertNotNull(d);
    assertNotNull(d.getKey());
    printDiff(DecisionMapperTest.removeCreatedProps(d), d1);
    assertEquals(DecisionMapperTest.removeCreatedProps(d), d1);
  }

  void printDiff(Object o1, Object o2) {
    Javers javers = JaversBuilder.javers().build();
    Diff diff = javers.compare(o1, o2);
    System.out.println(diff);
  }

  @Test
  public void usagesByIds() {
    List<String> ids = Lists.immutableListOf("55", "46");
    try (SqlSession session = SqlSessionFactoryRule.getSqlSessionFactory().openSession(true)) {
      var dm = session.getMapper(DuplicateMapper.class);
      List<Duplicate.UsageDecision> res = dm.usagesByIds(datasetKey, Datasets.COL, ids);
      assertEquals(2, res.size());
      for (Duplicate.UsageDecision u : res) {
        assertFalse(u.getClassification().isEmpty());
        assertNull(u.getDecision());
        assertNotNull(u.getUsage());
      }

      // test with larger number of parameter ids than postgres 32767 limit
      ids = IntStream.range(1, 50000).boxed().map(String::valueOf).collect(Collectors.toList());
      res = dm.usagesByIds(datasetKey, Datasets.COL, ids);
    }

  }
  
  @Test
  public void duplicates() {
    Set<TaxonomicStatus> status = new HashSet<>();
    status.add(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    List<Duplicate.Mybatis> dups = mapper.duplicates(MatchingMode.STRICT, null, 2, datasetKey, null, null, NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES), status, false, null, null, null, false, Datasets.COL,
        new Page(0, 2));
    assertEquals(2, dups.size());
    for (Duplicate.Mybatis d : dups) {
      assertFalse(d.getUsages().isEmpty());
      assertNotNull(d.getKey());
    }
    assertEquals((Integer) 3, mapper.count(MatchingMode.STRICT, null, 2, datasetKey, null, null, NameCategory.BINOMIAL,
      Sets.newHashSet(Rank.SPECIES), status, false, null, null, null, false, Datasets.COL));

    // all accepted, so not different
    // https://github.com/Sp2000/colplus-backend/issues/456
    dups = mapper.duplicates(MatchingMode.STRICT, null, 2, datasetKey, null, null, NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES), status, false, true, null, null, false, Datasets.COL,
        new Page(0, 2));
    assertEquals(2, dups.size());
    dups = mapper.duplicates(MatchingMode.STRICT, null, 2, datasetKey, null, null, NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES), status, false, false, null, null, false, Datasets.COL,
        new Page(0, 2));
    assertEquals(0, dups.size());
    dups = mapper.duplicates(MatchingMode.STRICT, null, 2, datasetKey, null, null, NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES), null, null, false, null, null, null, Datasets.COL,
        new Page(0, 2));
    assertEquals(1, dups.size());
    assertEquals("achillea nigra", dups.get(0).getKey());
    
    // https://github.com/Sp2000/colplus-backend/issues/457
    // Aspidoscelis deppii subsp. schizophorus
    dups = mapper.duplicates(MatchingMode.STRICT, null, 3, datasetKey, null, null, NameCategory.TRINOMIAL,
        Sets.newHashSet(Rank.SUBSPECIES), null, true, null, null, null, null, Datasets.COL,
        new Page(0, 5));
    assertEquals(1, dups.size());

    dups = mapper.duplicates(MatchingMode.FUZZY, null, 2, datasetKey, 999, null, null,
      null, null, true, null, null, null, null, null,
      new Page(0, 5));
    assertEquals(0, dups.size());

    dups = mapper.duplicates(MatchingMode.FUZZY, null, 2, datasetKey, null, 999, null,
      null, null, true, null, null, null, null, null,
      new Page(0, 5));
    assertEquals(0, dups.size());

    dups = mapper.duplicates(MatchingMode.FUZZY, null, 2, datasetKey, 999, 999, null,
      null, null, true, null, null, null, null, null,
      new Page(0, 5));
    assertEquals(0, dups.size());  }
  
  @Test
  public void duplicateNames() {
    List<Duplicate.Mybatis> dups = mapper.duplicateNames(MatchingMode.STRICT, null, 2, datasetKey,  NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES), false, false, false, new Page(0, 2));
    assertEquals(2, dups.size());
    for (Duplicate.Mybatis d : dups) {
      assertFalse(d.getUsages().isEmpty());
      assertNotNull(d.getKey());
    }
    assertEquals((Integer) 3, mapper.countNames(MatchingMode.STRICT, null, 2, datasetKey,  NameCategory.BINOMIAL,
      Sets.newHashSet(Rank.SPECIES), false, false, false));

    dups = mapper.duplicateNames(MatchingMode.STRICT, null, 2, datasetKey,  NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES), false, true, true, new Page(0, 2));
    assertEquals(0, dups.size());
  
    // https://github.com/Sp2000/colplus-backend/issues/457
    // Achillea asplenifolia
    dups = mapper.duplicates(MatchingMode.STRICT, null, 2, datasetKey, null, null, NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES_AGGREGATE), null, true, null, null, null, null, Datasets.COL,
        new Page(0, 5));
    assertEquals(1, dups.size());

    // Achillea
    dups = mapper.duplicates(MatchingMode.STRICT, "Achillea", 2, datasetKey, null, null, NameCategory.BINOMIAL,
      null, null, true, null, null, null, null, Datasets.COL,
      new Page(0, 5));
    assertEquals(2, dups.size());

    // Achillea asplenifolia
    dups = mapper.duplicates(MatchingMode.STRICT, "Achillea asp", 2, datasetKey, null, null, NameCategory.BINOMIAL,
      null, null, true, null, null, null, null, Datasets.COL,
      new Page(0, 5));
    assertEquals(1, dups.size());
  }
  
}