package life.catalogue.es.query;

import life.catalogue.es.EsNameUsage;
import life.catalogue.es.EsReadTestBase;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

import static org.gbif.nameparser.api.Rank.*;
import static org.junit.Assert.assertEquals;

public class RangeQueryTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test1() {
    EsNameUsage doc1 = new EsNameUsage();
    doc1.setRank(KINGDOM);
    EsNameUsage doc2 = new EsNameUsage();
    doc2.setRank(PHYLUM);
    EsNameUsage doc3 = new EsNameUsage();
    doc3.setRank(PHYLUM);
    EsNameUsage doc4 = new EsNameUsage();
    doc4.setRank(SPECIES);
    EsNameUsage doc5 = new EsNameUsage();
    doc5.setRank(SUBSPECIES);
    EsNameUsage doc6 = new EsNameUsage();
    doc6.setRank(SUBSPECIES);
    indexRaw(doc1, doc2, doc3, doc4, doc5, doc6);
    List<EsNameUsage> result = queryRaw(new RangeQuery<Integer>("rank").greaterOrEqual(SPECIES.ordinal()));
    assertEquals(3, result.size());
    result = queryRaw(new RangeQuery<Integer>("rank").greaterThan(SPECIES.ordinal()));
    assertEquals(2, result.size());
    result = queryRaw(new RangeQuery<Integer>("rank").lessOrEqual(SPECIES.ordinal()));
    assertEquals(4, result.size());
    result = queryRaw(new RangeQuery<Integer>("rank").lessThan(SPECIES.ordinal()));
    assertEquals(3, result.size());
  }

}
