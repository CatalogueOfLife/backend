package life.catalogue.printer.diff;

import life.catalogue.printer.NamesDiff;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Merge-join over two byte-ordered name streams. Pass 1 finds added/removed with O(1) memory over the
 * common set (only differences are buffered). Pass 2 (NamesDiffEngine.assemble) pairs similar
 * candidates into changed names. Local sort-order glitches surface as identical remove+add pairs and
 * are healed in pass 2; a runaway candidate count (comparator not matching the input order) trips
 * maxChangedCandidates and the result is truncated gracefully rather than failing.
 */
public class StreamingMergeDiffEngine implements NamesDiffEngine {
  private static final Logger LOG = LoggerFactory.getLogger(StreamingMergeDiffEngine.class);

  @Override
  public NamesDiff diff(DiffInput a, DiffInput b, DiffOptions opts) {
    final Comparator<String> order = opts.getOrder();
    final long cap = opts.getMaxChangedCandidates();
    final List<String> removed = new ArrayList<>();
    final List<String> added = new ArrayList<>();
    boolean inversionSeen = false;
    boolean capped = false;

    try (Stream<String> sa = a.lines().get(); Stream<String> sb = b.lines().get()) {
      Iterator<String> ia = sa.iterator();
      Iterator<String> ib = sb.iterator();
      String x = ia.hasNext() ? ia.next() : null;
      String y = ib.hasNext() ? ib.next() : null;
      String prevX = null, prevY = null;

      while (x != null && y != null) {
        if (prevX != null && order.compare(prevX, x) > 0) inversionSeen = true;
        if (prevY != null && order.compare(prevY, y) > 0) inversionSeen = true;
        int c = order.compare(x, y);
        if (c == 0) {
          prevX = x; prevY = y;
          x = ia.hasNext() ? ia.next() : null;
          y = ib.hasNext() ? ib.next() : null;
        } else if (c < 0) {
          removed.add(x); prevX = x;
          x = ia.hasNext() ? ia.next() : null;
        } else {
          added.add(y); prevY = y;
          y = ib.hasNext() ? ib.next() : null;
        }
        if (removed.size() + added.size() >= cap) { capped = true; break; }
      }
      while (!capped && x != null) {
        if (prevX != null && order.compare(prevX, x) > 0) inversionSeen = true;
        removed.add(x); prevX = x;
        x = ia.hasNext() ? ia.next() : null;
        if (removed.size() + added.size() >= cap) capped = true;
      }
      while (!capped && y != null) {
        if (prevY != null && order.compare(prevY, y) > 0) inversionSeen = true;
        added.add(y); prevY = y;
        y = ib.hasNext() ? ib.next() : null;
        if (removed.size() + added.size() >= cap) capped = true;
      }
    }

    if (inversionSeen) {
      LOG.warn("Diff inputs {} / {} were not strictly sorted under the configured order; relying on pass-2 healing",
        a.label(), b.label());
    }
    if (capped) {
      LOG.warn("Diff {} / {} exceeded {} candidate differences; result truncated", a.label(), b.label(), cap);
    }
    NamesDiff diff = NamesDiffEngine.assemble(a.label(), b.label(), removed, added, opts);
    if (capped) {
      diff.setTruncated(true);
    }
    return diff;
  }
}
