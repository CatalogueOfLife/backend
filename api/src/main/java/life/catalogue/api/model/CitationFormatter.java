package life.catalogue.api.model;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Hook that renders formatted citation strings for {@link Citation} and {@link Dataset}.
 * The citeproc-backed implementation lives in the reference module and is registered once at
 * application startup, keeping the api model free of citeproc. When no formatter is registered
 * (e.g. plain unit tests) the model's citation getters return null.
 */
public interface CitationFormatter {
  String citationHtml(Citation citation);
  String citationText(Citation citation);
  String citationHtml(Dataset dataset);
  String citationText(Dataset dataset);

  AtomicReference<CitationFormatter> INSTANCE = new AtomicReference<>();

  static void register(CitationFormatter formatter) {
    INSTANCE.set(formatter);
  }

  static CitationFormatter get() {
    return INSTANCE.get();
  }
}
