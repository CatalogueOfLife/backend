package life.catalogue.printer.diff;

import java.util.Comparator;

public class DiffOptions {

  /**
   * Compares by Unicode code point, which equals UTF-8 byte order for well-formed text and thus
   * matches Postgres LC_COLLATE 'C'. NOT String.compareTo (UTF-16 code-unit order differs for
   * supplementary characters).
   */
  public static final Comparator<String> CODEPOINT = (a, b) -> {
    int i = 0, j = 0;
    final int la = a.length(), lb = b.length();
    while (i < la && j < lb) {
      int ca = a.codePointAt(i);
      int cb = b.codePointAt(j);
      if (ca != cb) {
        return Integer.compare(ca, cb);
      }
      i += Character.charCount(ca);
      j += Character.charCount(cb);
    }
    return Integer.compare(la - i, lb - j);
  };

  private Comparator<String> order = CODEPOINT;
  private int canonicalMaxDistance = 1;      // max Levenshtein distance between normalised canonicals to pair as "changed"
  private int maxItems = 0;                  // 0 = unlimited output per list
  private long maxChangedCandidates = 1_000_000L; // OOM backstop for pass-1 buffers (~150MB peak)

  public static DiffOptions defaults() {
    return new DiffOptions();
  }

  public Comparator<String> getOrder() { return order; }
  public DiffOptions setOrder(Comparator<String> order) { this.order = order; return this; }

  public int getCanonicalMaxDistance() { return canonicalMaxDistance; }
  public DiffOptions setCanonicalMaxDistance(int d) { this.canonicalMaxDistance = d; return this; }

  public int getMaxItems() { return maxItems; }
  public DiffOptions setMaxItems(int maxItems) { this.maxItems = maxItems; return this; }

  public long getMaxChangedCandidates() { return maxChangedCandidates; }
  public DiffOptions setMaxChangedCandidates(long v) { this.maxChangedCandidates = v; return this; }
}
