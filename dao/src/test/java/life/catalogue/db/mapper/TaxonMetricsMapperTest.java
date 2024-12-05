package life.catalogue.db.mapper;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.TaxonMetrics;

import org.gbif.nameparser.api.Rank;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import static life.catalogue.api.model.SimpleName.sn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TaxonMetricsMapperTest extends MapperTestBase<TaxonMetricsMapper> {


  public TaxonMetricsMapperTest() {
    super(TaxonMetricsMapper.class);
  }

  @Test
  public void getEmpty() throws Exception {
    assertNull(mapper().get(DSID.of(11, "")));
  }

  @Test
  public void roundtrip() throws Exception {
    TaxonMetrics tm1 = new TaxonMetrics();
    tm1.setId(TestEntityGenerator.TAXON1.getId());
    tm1.setDatasetKey(TestEntityGenerator.TAXON1.getDatasetKey());
    tm1.setTaxonCount(23456);
    tm1.setMaxDepth(67);
    tm1.setDepth(13);
    tm1.setChildCount(7);
    tm1.setChildExtantCount(1);
    tm1.setLft(1);
    tm1.setRgt(40);
    tm1.setTaxaByRankCount(Map.of(Rank.SPECIES, 13, Rank.GENUS, 7));
    tm1.setSpeciesBySourceCount(Map.of(1,2,3,4,5,6));
    tm1.setClassification(List.of(sn(Rank.FAMILY, "Pinaceae"), sn("Abies"), sn(Rank.SPECIES, "Abies alba", "Miller")));
    tm1.setSourceDatasetKeys(IntOpenHashSet.of(234,6,21324));

    mapper().create(tm1);
    commit();

    TaxonMetrics tm2 = mapper().get(tm1.getKey());
    printDiff(tm1, tm2);
    assertEquals(tm1, tm2);
  }
}