package life.catalogue.release;

import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.assembly.SectorSyncIT;
import life.catalogue.db.PgSetupRule;
import life.catalogue.db.TestDataRule;
import life.catalogue.db.mapper.SectorMapper;
import org.apache.ibatis.session.SqlSession;
import org.gbif.nameparser.api.Rank;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;

import static org.junit.Assert.assertEquals;

public class TableCopyHandlerWithKeyMapTest {

  public final static PgSetupRule pg = new PgSetupRule();
  public final static TestDataRule dataRule = TestDataRule.apple();

  @ClassRule
  public final static TestRule chain = RuleChain
    .outerRule(pg)
    .around(dataRule);

  @Test
  public void accept() {
    // prepare 2 sectors
    int s1 = SectorSyncIT.createSector(Sector.Mode.ATTACH, TestDataRule.TestData.APPLE.key,
      new SimpleName("123", "Theridiidae", Rank.FAMILY),
      new SimpleName("t123", "Theridiidae", Rank.FAMILY)
    );
    int s2 = SectorSyncIT.createSector(Sector.Mode.ATTACH, TestDataRule.TestData.APPLE.key,
      new SimpleName("1234", "Asteraceae", Rank.FAMILY),
      new SimpleName("t1234", "Asteraceae", Rank.FAMILY)
    );

    // test copy handler
    TableCopyHandlerWithKeyMap<Sector, SectorMapper> handler = new TableCopyHandlerWithKeyMap<>(12, PgSetupRule.getSqlSessionFactory(),
      Sector.class.getSimpleName(), SectorMapper.class);

    try (SqlSession session = PgSetupRule.getSqlSessionFactory().openSession()) {
      session.getMapper(SectorMapper.class).processSectors(Datasets.DRAFT_COL, TestDataRule.TestData.APPLE.key).forEach(handler);
    } finally {
      handler.close();
    }
    assertEquals(2, handler.getKeyMap().size());
  }
}