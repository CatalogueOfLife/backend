package life.catalogue.common.csl;

import life.catalogue.api.model.CslData;

import java.util.concurrent.atomic.AtomicInteger;

import de.undercouch.citeproc.csl.CSLItemData;

/**
 * A fixed set of {@link CslFormatter} instances for one style and format, handed out round robin.
 *
 * A single formatter renders one citation at a time - {@link CslFormatter#cite} is synchronized because the
 * underlying CSL processor and its item provider are stateful. With a single shared instance a request that
 * renders many citations, e.g. the metadata of a release with thousands of sources, therefore blocks every
 * other citation rendering in the JVM for its entire duration. A handful of instances divides that contention
 * without changing how any single rendering works.
 *
 * Holding several is cheap: citeproc-java is a pure java implementation with no script engine behind it,
 * so an instance is little more than the parsed CSL style.
 */
public class CslFormatterPool {
  private final CslFormatter[] formatters;
  private final AtomicInteger next = new AtomicInteger();

  public CslFormatterPool(CslFormatter.STYLE style, CslFormatter.FORMAT format, int size) {
    if (size < 1) {
      throw new IllegalArgumentException("A formatter pool needs at least one formatter");
    }
    formatters = new CslFormatter[size];
    for (int i = 0; i < size; i++) {
      formatters[i] = new CslFormatter(style, format);
    }
  }

  /**
   * @return the next formatter in the rotation. floorMod keeps this correct once the counter wraps.
   */
  private CslFormatter next() {
    return formatters[Math.floorMod(next.getAndIncrement(), formatters.length)];
  }

  public String cite(CslData data) {
    return next().cite(data);
  }

  public String cite(CSLItemData data) {
    return next().cite(data);
  }

  public int size() {
    return formatters.length;
  }
}
