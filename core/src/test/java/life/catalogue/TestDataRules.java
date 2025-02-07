package life.catalogue;

import life.catalogue.api.vocab.DataFormat;
import life.catalogue.junit.TestDataRule;

import java.util.Map;
import java.util.Set;

import it.unimi.dsi.fastutil.Pair;

/**
 * More test data rules that have been generated via the TestDataGenerator class in the importer module.
 */
public class TestDataRules {
  final static TestDataRule.TestData MATCHING = new TestDataRule.TestData("matching", 101, Set.of(101, 102), false);

  final static TestDataRule.TestData SYNCS = new TestDataRule.TestData("syncs", 3, null, false, Map.ofEntries(
     Map.entry(Pair.of(DataFormat.COLDP, 38), 118),
     Map.entry(Pair.of(DataFormat.DWCA, 1), 106),
     Map.entry(Pair.of(DataFormat.COLDP, 35), 117),
     Map.entry(Pair.of(DataFormat.DWCA, 2), 107),
     Map.entry(Pair.of(DataFormat.COLDP, 4), 112),
     Map.entry(Pair.of(DataFormat.ACEF, 14), 120),
     Map.entry(Pair.of(DataFormat.COLDP, 2), 111),
     Map.entry(Pair.of(DataFormat.COLDP, 34), 116),
     Map.entry(Pair.of(DataFormat.COLDP, 0), 103),
     Map.entry(Pair.of(DataFormat.ACEF, 11), 110),
     Map.entry(Pair.of(DataFormat.COLDP, 27), 101),
     Map.entry(Pair.of(DataFormat.COLDP, 25), 105),
     Map.entry(Pair.of(DataFormat.COLDP, 26), 115),
     Map.entry(Pair.of(DataFormat.COLDP, 24), 114),
     Map.entry(Pair.of(DataFormat.ACEF, 1), 102),
     Map.entry(Pair.of(DataFormat.COLDP, 22), 104),
     Map.entry(Pair.of(DataFormat.DWCA, 45), 119),
     Map.entry(Pair.of(DataFormat.COLDP, 14), 113),
     Map.entry(Pair.of(DataFormat.ACEF, 6), 109),
     Map.entry(Pair.of(DataFormat.ACEF, 5), 108)
  ));
  final static TestDataRule.TestData XCOL = new TestDataRule.TestData("xcol", 3, null, false);
  final static TestDataRule.TestData GROUPING = new TestDataRule.TestData("homgroup", 4, null, false);

  public static TestDataRule homotypigGrouping() {
    return new TestDataRule(GROUPING);
  }
  public static TestDataRule matching() {
    return new TestDataRule(MATCHING);
  }
  public static TestDataRule syncs() {
    return new TestDataRule(SYNCS);
  }
  public static TestDataRule xcol() {
    return new TestDataRule(XCOL);
  }

}