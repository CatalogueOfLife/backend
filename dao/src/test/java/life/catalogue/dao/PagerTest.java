package life.catalogue.dao;

import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
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
}