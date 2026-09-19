package life.catalogue.matching;

import life.catalogue.api.model.*;
import life.catalogue.matching.nidx.NameIndex;

import life.catalogue.parser.NameParser;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MatchingUtils {
  private final NameIndex nameIndex;

  public MatchingUtils(NameIndex nameIndex) {
    this.nameIndex = nameIndex;
  }

  /**
   * @return the names index id of the usages name, matching it first if it has none yet. Null if it cannot be matched
   */
  public Integer nidxAndMatchIfNeeded(NameUsageBase nu, boolean allowInserts) {
    // the names index id is the only persisted signal we have - a null id means we have not matched yet
    // (we no longer distinguish an unmatched name from one that was never attempted)
    if (nu.getName().getNamesIndexId() == null) {
      var match = nameIndex.match(nu.getName(), allowInserts, false);
      if (match.isMatched()) {
        nu.getName().setNamesIndexId(match.getNidx());
      }
    }
    return nu.getName().getNamesIndexId();
  }

  /**
   * @return the simple name, matched to the names index!
   */
  public SimpleNameCached toSimpleNameCached(NameUsageBase nu) {
    if (nu != null) {
      return new SimpleNameCached(nu, nidxAndMatchIfNeeded(nu, true));
    }
    return null;
  }

  public static List<SimpleNameCached> toSimpleNameCached(SimpleName[] classification) {
    return classification == null ? null : Arrays.stream(classification).map(SimpleNameCached::new).collect(Collectors.toList());
  }
  public static List<SimpleNameCached> toSimpleNameCached(List<SimpleName> classification) {
    return classification == null ? null : classification.stream().map(SimpleNameCached::new).collect(Collectors.toList());
  }

  /**
   * @return the classified name, matched to the names index!
   */
  public SimpleNameClassified<SimpleNameCached> toSimpleNameClassified(NameUsageBase nu, List<SimpleNameCached> classification) {
    SimpleNameClassified<SimpleNameCached> snc = null;
    if (nu != null) {
      snc = new SimpleNameClassified<>(nu, nidxAndMatchIfNeeded(nu, true));
      snc.setClassification(classification);
    }
    return snc;
  }

  /**
   * Convert a simple name classification to a cached one.
   */
  public SimpleNameClassified<SimpleNameCached> toSimpleNameClassified(SimpleNameClassified<SimpleName> sn) {
    SimpleNameClassified<SimpleNameCached> snc = new SimpleNameClassified<SimpleNameCached>(sn);
    if (sn.getClassification() != null) {
      snc.setClassification(sn.getClassification().stream().map(SimpleNameCached::new).collect(Collectors.toList()));
    }
    return snc;
  }

}
