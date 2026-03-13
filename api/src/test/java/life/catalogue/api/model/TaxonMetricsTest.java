package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.SerdeTestBase;
import life.catalogue.api.vocab.Datasets;

import life.catalogue.common.collection.CountMap;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import org.junit.Test;

import static life.catalogue.api.model.SimpleName.sn;
import static life.catalogue.common.collection.CollectionUtils.countable;
import static life.catalogue.common.collection.CollectionUtils.mutable;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TaxonMetricsTest  extends SerdeTestBase<TaxonMetrics> {

  private TaxonMetrics tm;

  public TaxonMetricsTest() {
    super(TaxonMetrics.class);
  }

  @Test
  public void add() throws Exception {
    var tm = genTestValue();
    var tm2 = genTestValue();
    tm.add(tm2);
    assertEquals(tm.getTaxonCount(), 2*tm2.getTaxonCount());
    assertEquals(tm.getSpeciesCount(), 2* tm2.getSpeciesCount());
    assertEquals(tm.getSpeciesExtantCount(), 2* tm2.getSpeciesExtantCount());
    var taxaByRankCount = new CountMap<>(tm2.getTaxaByRankCount());
    taxaByRankCount.multiply(2);
    assertEquals(tm.getTaxaByRankCount(), taxaByRankCount);
    var speciesBySourceCount = new CountMap<>(tm2.getSpeciesBySourceCount());
    speciesBySourceCount.multiply(2);
    assertEquals(tm.getSpeciesBySourceCount(), speciesBySourceCount);
    // stays the same
    assertEquals(tm.getMaxDepth(), tm2.getMaxDepth());
    assertEquals(tm.getDatasetKey(), tm2.getDatasetKey());
    assertEquals(tm.getId(), tm2.getId());
    assertEquals(tm.getDepth(), tm2.getDepth());
    assertEquals(tm.getChildCount(), tm2.getChildCount());
    assertEquals(tm.getChildExtantCount(), tm2.getChildExtantCount());
    assertEquals(tm.getLft(), tm2.getLft());
    assertEquals(tm.getRgt(), tm2.getRgt());

    tm2.setMaxDepth(100);
    tm.add(tm2);
    assertEquals(tm.getMaxDepth(), tm2.getMaxDepth());
  }

  @Override
  public TaxonMetrics genTestValue() throws Exception {
    tm = new TaxonMetrics();
    tm.setId(TestEntityGenerator.TAXON1.getId());
    tm.setDatasetKey(Datasets.COL);
    tm.setTaxonCount(23456);
    tm.setMaxDepth(67);
    tm.setDepth(13);
    tm.setChildCount(7);
    tm.setChildExtantCount(1);
    tm.setSpeciesExtantCount(2);
    tm.setLft(1);
    tm.setRgt(40);
    tm.setTaxaByRankCount(countable(Map.of(Rank.SPECIES, 13, Rank.GENUS, 7)));
    tm.setSpeciesBySourceCount(countable(Map.of(1,2,3,4,5,6)));
    tm.setClassification(mutable(List.of(sn(Rank.FAMILY, "Pinaceae"), sn("Abies"), sn(Rank.SPECIES, "Abies alba", "Miller"))));
    tm.setSourceDatasetKeys(IntOpenHashSet.of(234,6,21324));
    return tm;
  }
}