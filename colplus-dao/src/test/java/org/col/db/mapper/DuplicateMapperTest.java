package org.col.db.mapper;

import java.sql.Connection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.Duplicate;
import org.col.api.model.Page;
import org.col.api.vocab.MatchingMode;
import org.col.api.vocab.NameCategory;
import org.col.api.vocab.TaxonomicStatus;
import org.col.common.tax.SciNameNormalizer;
import org.col.db.PgSetupRule;
import org.col.postgres.AuthorshipNormFunc;
import org.col.postgres.PgCopyUtils;
import org.gbif.nameparser.api.Rank;
import org.javers.common.collections.Lists;
import org.junit.*;
import org.postgresql.jdbc.PgConnection;

import static org.junit.Assert.*;

public class DuplicateMapperTest {
  
  final static int datasetKey = 1000;
  DuplicateMapper mapper;
  SqlSession session;
  
  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();
  
  @BeforeClass
  public static void setup() throws Exception {
    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession(true)) {
      final DatasetPartitionMapper pm = session.getMapper(DatasetPartitionMapper.class);
      pm.create(datasetKey);
      pm.buildIndices(datasetKey);
      pm.attach(datasetKey);
      session.commit();
    }
  
    final AuthorshipNormFunc aFunc = new AuthorshipNormFunc(18);
    try (Connection c = pgSetupRule.connect()) {
      PgConnection pgc = (PgConnection) c;
      
      PgCopyUtils.copy(pgc, "dataset", "/duplicates/dataset.csv");
      PgCopyUtils.copy(pgc, "verbatim_1000", "/duplicates/verbatim.csv");
      PgCopyUtils.copy(pgc, "name_1000", "/duplicates/name.csv", null, ImmutableMap.<String, Function<String[], String>>of(
          "scientific_name_normalized", row -> SciNameNormalizer.normalize(row[6]),
          "authorship_normalized", aFunc::normAuthorship
          )
      );
      PgCopyUtils.copy(pgc, "name_usage_1000", "/duplicates/name_usage.csv");
      
      c.commit();
    }
  }
  
  @Before
  public void init() {
    session = PgSetupRule.getSqlSessionFactory().openSession(true);
    mapper = session.getMapper(DuplicateMapper.class);
  }
  
  @After
  public void destroy() {
    session.close();
  }
  
  @Test
  public void usagesByIds() {
    List<Duplicate.UsageDecision> res = mapper.usagesByIds(datasetKey, Lists.immutableListOf("45", "46"));
    assertEquals(2, res.size());
    for (Duplicate.UsageDecision u : res) {
      assertFalse(u.getClassification().isEmpty());
    }
  }
  
  @Test
  public void duplicates() {
    Set<TaxonomicStatus> status = new HashSet<>();
    status.add(TaxonomicStatus.PROVISIONALLY_ACCEPTED);
    List<Duplicate.Mybatis> dups = mapper.duplicates(MatchingMode.STRICT, 2, datasetKey, null, NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES), status, false, false, false,
        new Page(0, 2));
    assertEquals(2, dups.size());
    for (Duplicate.Mybatis d : dups) {
      assertFalse(d.getUsages().isEmpty());
      assertNotNull(d.getKey());
    }
  }
  
  @Test
  public void duplicateNames() {
    List<Duplicate.Mybatis> dups = mapper.duplicateNames(MatchingMode.STRICT, 2, datasetKey,  NameCategory.BINOMIAL,
        Sets.newHashSet(Rank.SPECIES), false, new Page(0, 2));
    assertEquals(2, dups.size());
    for (Duplicate.Mybatis d : dups) {
      assertFalse(d.getUsages().isEmpty());
      assertNotNull(d.getKey());
    }
  }
  
}