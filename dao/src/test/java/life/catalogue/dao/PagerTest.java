package life.catalogue.dao;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.EditorialDecision;
import life.catalogue.api.search.DecisionSearchRequest;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.DecisionMapper;
import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class PagerTest {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  @Rule
  public final TestDataRule testDataRule = TestDataRule.draftWithSectors();


  @Test
  public void sectors() {
    AtomicInteger counter = new AtomicInteger();
    Pager.sectors(888, PgSetupRule.getSqlSessionFactory()).forEach(s -> {
      counter.incrementAndGet();
    });
    assertEquals(0, counter.get());

    Pager.sectors(100, PgSetupRule.getSqlSessionFactory()).forEach(s -> {
      counter.incrementAndGet();
    });
    assertEquals(0, counter.get());

    Pager.sectors(TestDataRule.TestData.DRAFT_WITH_SECTORS.key, PgSetupRule.getSqlSessionFactory()).forEach(s -> {
      counter.incrementAndGet();
    });
    assertEquals(1, counter.get());
  }

  @Test
  public void decisions() {
    AtomicInteger counter = new AtomicInteger();

    Pager.decisions(TestDataRule.TestData.DRAFT_WITH_SECTORS.key, PgSetupRule.getSqlSessionFactory()).forEach(s -> {
      counter.incrementAndGet();
    });
    assertEquals(0, counter.get());

    Pager.decisions(8910, PgSetupRule.getSqlSessionFactory()).forEach(s -> {
      counter.incrementAndGet();
    });
    assertEquals(0, counter.get());


    Pager.decisions(PgSetupRule.getSqlSessionFactory(), DecisionSearchRequest.byDataset(TestDataRule.TestData.DRAFT_WITH_SECTORS.key, 1)).forEach(s -> {
      counter.incrementAndGet();
    });
    assertEquals(0, counter.get());

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession()) {
      DecisionMapper dm = session.getMapper(DecisionMapper.class);
      // 110 is above page size of 100
      for (int x = 0; x<100; x++){
        EditorialDecision d = TestEntityGenerator.newDecision(TestDataRule.TestData.DRAFT_WITH_SECTORS.key, 1, "id"+x);
        dm.create(d);
      }
      session.commit();
    }

    //
    Pager.decisions(PgSetupRule.getSqlSessionFactory(), DecisionSearchRequest.byDataset(TestDataRule.TestData.DRAFT_WITH_SECTORS.key, 1)).forEach(s -> {
      counter.incrementAndGet();
    });
    assertEquals(100, counter.get());
    counter.set(0);

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession()) {
      DecisionMapper dm = session.getMapper(DecisionMapper.class);
      for (int x = 100; x<220; x++){
        EditorialDecision d = TestEntityGenerator.newDecision(TestDataRule.TestData.DRAFT_WITH_SECTORS.key, 1, "id"+x);
        dm.create(d);
      }
      session.commit();
    }

    Pager.decisions(PgSetupRule.getSqlSessionFactory(), DecisionSearchRequest.byDataset(TestDataRule.TestData.DRAFT_WITH_SECTORS.key, 1)).forEach(s -> {
      counter.incrementAndGet();
    });
    assertEquals(220, counter.get());
  }
}