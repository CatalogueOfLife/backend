package life.catalogue.es.query;

import java.util.List;

import life.catalogue.es.EsReadTestBase;
import life.catalogue.es.model.NameUsageDocument;
import org.junit.Before;
import org.junit.Test;

import static org.gbif.nameparser.api.Rank.KINGDOM;
import static org.gbif.nameparser.api.Rank.PHYLUM;
import static org.gbif.nameparser.api.Rank.SPECIES;
import static org.gbif.nameparser.api.Rank.SUBSPECIES;
import static org.junit.Assert.assertEquals;

public class RangeQueryTest extends EsReadTestBase {

  @Before
  public void before() {
    destroyAndCreateIndex();
  }

  @Test
  public void test1() {
    NameUsageDocument doc1 = new NameUsageDocument();
    doc1.setRank(KINGDOM);
    NameUsageDocument doc2 = new NameUsageDocument();
    doc2.setRank(PHYLUM);
    NameUsageDocument doc3 = new NameUsageDocument();
    doc3.setRank(PHYLUM);
    NameUsageDocument doc4 = new NameUsageDocument();
    doc4.setRank(SPECIES);
    NameUsageDocument doc5 = new NameUsageDocument();
    doc5.setRank(SUBSPECIES);
    NameUsageDocument doc6 = new NameUsageDocument();
    doc6.setRank(SUBSPECIES);
    indexRaw(doc1, doc2, doc3, doc4, doc5, doc6);
    List<NameUsageDocument> result = queryRaw(new RangeQuery<Integer>("rank").greaterOrEqual(SPECIES.ordinal()));
    assertEquals(3, result.size());
    result = queryRaw(new RangeQuery<Integer>("rank").greaterThan(SPECIES.ordinal()));
    assertEquals(2, result.size());
    result = queryRaw(new RangeQuery<Integer>("rank").lessOrEqual(SPECIES.ordinal()));
    assertEquals(4, result.size());
    result = queryRaw(new RangeQuery<Integer>("rank").lessThan(SPECIES.ordinal()));
    assertEquals(3, result.size());
  }

}
