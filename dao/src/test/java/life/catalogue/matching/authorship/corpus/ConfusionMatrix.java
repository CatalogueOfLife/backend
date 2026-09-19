package life.catalogue.matching.authorship.corpus;

import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.corpus.LabelRules.Label;

/**
 * What the comparator said about the pairs of each label, counted per pair and weighted by the number of names
 * that back a pair. Only SAME and DIFF are measured.
 */
public class ConfusionMatrix {
  private static final Label[] LABELS = {Label.SAME, Label.DIFF};
  private static final Equality[] VERDICTS = {Equality.EQUAL, Equality.DIFFERENT, Equality.UNKNOWN};

  private final long[][] counts = new long[Label.values().length][Equality.values().length];
  private final long[][] weights = new long[Label.values().length][Equality.values().length];

  public void add(Label label, Equality verdict, int weight) {
    counts[label.ordinal()][verdict.ordinal()]++;
    weights[label.ordinal()][verdict.ordinal()] += weight;
  }

  public long count(Label label, Equality verdict) {
    return counts[label.ordinal()][verdict.ordinal()];
  }

  public long weight(Label label, Equality verdict) {
    return weights[label.ordinal()][verdict.ordinal()];
  }

  public long total() {
    long total = 0;
    for (Label l : LABELS) {
      total += total(counts, l);
    }
    return total;
  }

  private static long total(long[][] numbers, Label label) {
    long total = 0;
    for (Equality v : VERDICTS) {
      total += numbers[label.ordinal()][v.ordinal()];
    }
    return total;
  }

  public String render(String title) {
    StringBuilder sb = new StringBuilder();
    sb.append(title).append('\n');
    sb.append(String.format("  %-6s %30s   %30s%n", "", "pairs", "weighted by names"));
    sb.append(String.format("  %-6s %10s %10s %8s   %10s %10s %8s%n",
      "label", "EQUAL", "DIFFERENT", "UNKNOWN", "EQUAL", "DIFFERENT", "UNKNOWN"));
    for (Label l : LABELS) {
      sb.append(String.format("  %-6s", l));
      for (long[][] numbers : new long[][][]{counts, weights}) {
        long total = total(numbers, l);
        for (Equality v : VERDICTS) {
          long n = numbers[l.ordinal()][v.ordinal()];
          sb.append(String.format(v == Equality.UNKNOWN ? " %8s" : " %10s", total == 0 ? "-" : String.format("%.1f%%", 100d * n / total)));
        }
        sb.append("  ");
      }
      sb.append(String.format(" n=%,d / %,d%n", total(counts, l), total(weights, l)));
    }
    return sb.toString();
  }
}
