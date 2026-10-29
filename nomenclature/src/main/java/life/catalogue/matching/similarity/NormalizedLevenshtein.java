package life.catalogue.matching.similarity;

/**
 * Whole-string normalized Levenshtein similarity: 100 * (1 - editDistance / maxLength).
 * Unlike DistanceUtils.convertEditDistanceToSimilarity (tuned for fuzzy typo matching, which
 * zeroes out on multi-char additions), this rewards strings that share most of their content,
 * so it correctly scores authorship/year additions and small edits used for the "changed" pairing.
 */
public class NormalizedLevenshtein implements StringSimilarity {
  @Override
  public double getSimilarity(String x1, String x2) {
    if (x1.equals(x2)) return 100d;
    int max = Math.max(x1.length(), x2.length());
    if (max == 0) return 100d;
    return 100d * (1d - (double) LevenshteinDistance.getDistance(x1, x2) / max);
  }
}
