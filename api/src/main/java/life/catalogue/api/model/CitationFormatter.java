package life.catalogue.api.model;

import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hook that renders formatted citation strings for {@link Citation} and {@link Dataset}.
 * The citeproc-backed implementation lives in the reference module and is registered once at
 * application startup, keeping the api model free of citeproc.
 *
 * <p>Any process that has the reference module on its classpath also gets the implementation
 * auto-discovered lazily via {@link ServiceLoader} on first {@link #get()} call, so server
 * processes that never call {@link #register(CitationFormatter)} explicitly (e.g. the read-only
 * and matching servers) still render real citations. An explicit {@link #register} always wins.
 * When no implementation is on the classpath (e.g. plain api unit tests) the model's citation
 * getters keep returning null.
 */
public interface CitationFormatter {
  String citationHtml(Citation citation);
  String citationText(Citation citation);
  String citationHtml(Dataset dataset);
  String citationText(Dataset dataset);

  AtomicReference<CitationFormatter> INSTANCE = new AtomicReference<>();
  /** Guard so ServiceLoader is scanned at most once when no implementation is present. */
  boolean[] SERVICE_LOADER_TRIED = new boolean[1];

  static void register(CitationFormatter formatter) {
    INSTANCE.set(formatter);
  }

  static CitationFormatter get() {
    CitationFormatter cf = INSTANCE.get();
    if (cf == null) {
      cf = discover();
    }
    return cf;
  }

  private static CitationFormatter discover() {
    synchronized (SERVICE_LOADER_TRIED) {
      // re-check under the lock in case another thread just registered/discovered one
      CitationFormatter cf = INSTANCE.get();
      if (cf != null || SERVICE_LOADER_TRIED[0]) {
        return cf;
      }
      SERVICE_LOADER_TRIED[0] = true;
      Iterator<CitationFormatter> iter = ServiceLoader.load(CitationFormatter.class).iterator();
      if (iter.hasNext()) {
        CitationFormatter impl = iter.next();
        INSTANCE.compareAndSet(null, impl);
        return INSTANCE.get();
      }
      return null;
    }
  }
}
