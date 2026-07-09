package life.catalogue.matching;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.nidx.NameIndex;

import life.catalogue.parser.NameParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MatchingUtils {
  private static final Logger LOG = LoggerFactory.getLogger(MatchingUtils.class);
  private final NameIndex nameIndex;

  public MatchingUtils(NameIndex nameIndex) {
    this.nameIndex = nameIndex;
  }

  public static NidxMatch noMatch() {
    return new NidxMatch(null, null, MatchType.NONE);
  }

  public static class NidxMatch {
    public final Integer id;
    public final Integer canonicalId;
    public final MatchType matchType;

    public NidxMatch(Integer id, Integer canonicalId, MatchType matchType) {
      this.id = id;
      this.canonicalId = canonicalId;
      this.matchType = matchType;
    }

    public boolean hasNidx() {
      return id != null;
    }

    public DSIDValue<Integer> canonicalDSID(int datasetKey){
      return new DSIDValue<>(datasetKey, canonicalId);
    }
  }

  /**
   * @return a wrapper class that is never null. It holds the canonical names index id or null if it cant be matched
   */
  public NidxMatch nidxAndMatchIfNeeded(NameUsageBase nu, boolean allowInserts) {
    // the names index id is the only persisted signal we have - a null id means we have not matched yet
    // (we no longer distinguish an unmatched name from one that was never attempted)
    if (nu.getName().getNamesIndexId() == null) {
      // try to match
      var match = nameIndex.match(nu.getName(), allowInserts, false);
      if (match.hasMatch()) {
        nu.getName().setNamesIndexId(match.getName().getKey());
      }
      return match.hasMatch() ? new NidxMatch( match.getName().getKey(), match.getName().getKey(), match.getType()) : MatchingUtils.noMatch();

    } else {
      // lookup canonical nidx
      var xn = nameIndex.get(nu.getName().getNamesIndexId());
      if (xn == null) { // this is impossible unless data is out of sync
        throw new IllegalStateException("Missing names index entry " + nu.getName().getNamesIndexId());
      }
      return new NidxMatch(xn.getKey(), xn.getKey(), MatchType.EXACT);
    }
  }

  /**
   * @return the simple name, matched to the names index!
   */
  public SimpleNameCached toSimpleNameCached(NameUsageBase nu) {
    if (nu != null) {
      var nidx = nidxAndMatchIfNeeded(nu, true);
      return new SimpleNameCached(nu, nidx.canonicalId);
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
      var nidx = nidxAndMatchIfNeeded(nu, true);
      snc = new SimpleNameClassified<>(nu, nidx.canonicalId);
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
