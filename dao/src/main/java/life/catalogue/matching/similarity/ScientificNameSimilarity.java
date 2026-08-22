package life.catalogue.matching.similarity;

import life.catalogue.common.tax.SciNameNormalizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Apply normalizations to scientific names before scoring them for similarity
 * using edit distance applied to each epithet.
 */
public class ScientificNameSimilarity implements StringSimilarity {
  
  private static final Logger LOG = LoggerFactory.getLogger(ScientificNameSimilarity.class);
  
  private static final int MUST_MATCH = 4;
  
  ModifiedDamerauLevenshtein mdl1 = new ModifiedDamerauLevenshtein(1);
  ModifiedDamerauLevenshtein mdl3 = new ModifiedDamerauLevenshtein(3);
  
  @Override
  public double getSimilarity(String x1, String x2) {
    if (x1.equals(x2)) return 100d;
    
    x1 = SciNameNormalizer.normalize(x1);
    x2 = SciNameNormalizer.normalize(x2);
    
    String[] x1s = x1.split(" ");
    String[] x2s = x2.split(" ");
    
    // Compare the whole name if they don't have the same number of tokens.
    if (x1s.length != x2s.length) {
      double sim = mdl3.getSimilarity(x1, x2);
      LOG.debug("'{}' is {}% like '{}' (but lengths differ)", x1, sim, x2);
      return sim;
    }
    
    boolean bad = false;
    double overallSim = 0;
    int i;
    for (i = 0; i < x1s.length; i++) {
      double sim = similarity(x1s[i], x2s[i], i == 0);
      // The score of the first epithet (e.g. genus) is scaled down, 100→100, 50→0, <50→0
      if (i == 0) {
        sim = Math.max(0, (2 * sim - 100));
      }
      overallSim += sim;
      
      if (sim == 0) bad = true;
    }
    overallSim = overallSim / i;
    
    // Any epithet that doesn't match enough makes the whole match bad.
    if (bad && overallSim > 5) {
      overallSim = 5;
    }
    
    LOG.debug("'{}' is {}% like '{}'", x1, overallSim, x2);
    return overallSim;
  }
  
  /**
   * @param genus true if this is the first part of the name, i.e. the genus or a monomial
   */
  private double similarity(String x1, String x2, boolean genus) {
    // Very short epithets must match exactly
    if (x1.length() < MUST_MATCH || x2.length() < MUST_MATCH) {
      if (x1.equals(x2)) {
        LOG.debug("'{}' is short and exactly like '{}'", x1, x2);
        return 100;
      } else {
        LOG.debug("'{}' is short and nothing like '{}'", x1, x2);
        return 0;
      }
    }

    // First letter must match
    if (x1.charAt(0) != x2.charAt(0)) {
      LOG.debug("'{}' is not at all like '{}' ('{}'≠'{}')", x1, x2, x1.charAt(0), x2.charAt(0));
      return 0;
    }

    // More than one change in the first MUST_MATCH letters means very different things for a genus
    // and for an epithet, so the two are treated differently here.
    // Genus names are highly diverse and a single character usually means a genuinely different
    // genus, so such a genus is rejected outright.
    // An epithet is far more likely to be a simple typo: deleting one character shifts everything
    // after it along, so whether the first MUST_MATCH letters differ by one edit or by two depends
    // only on where in the word the typo fell, not on how different the epithets really are.
    // Such an epithet is still scored, but tolerating a single edit only.
    // See https://github.com/gbif/matching-ws/issues/13
    String x1head = x1.substring(0, MUST_MATCH);
    String x2head = x2.substring(0, MUST_MATCH);
    int dist = mdl1.getEditDistance(x1head, x2head);
    final boolean headDiffers = dist > 1;
    if (genus && headDiffers) {
      LOG.debug("genus '{}' is nothing like '{}' ('{}'≠'{}', dist={})", x1, x2, x1head, x2head, dist);
      return 0;
    }

    // And up to two changes in the whole epithet, or a single one if its first letters differ
    // TODO: Use Markus’ distance utility thing to take account of length.
    dist = mdl1.getEditDistance(x1, x2);
    double r;
    if (headDiffers) {
      r = dist == 1 ? 90 : 0;
    } else {
      r = (dist == 0 ? 100 : (dist == 1 ? 90 : (dist <= 2 ? 80 : 0)));
    }

    LOG.debug("'{}' is {}% like '{}'", x1, r, x2);
    return r;
  }
//
//
//
}
