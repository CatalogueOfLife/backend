package life.catalogue.common.csl;

import life.catalogue.api.model.CitationFormatter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Proves that {@link CitationFormatter#get()} auto-discovers the citeproc-backed
 * {@link CslCitationFormatter} purely via {@link java.util.ServiceLoader}, without any explicit
 * {@link CitationFormatter#register}. This guards the read-only / matching server processes that
 * never register the formatter explicitly against emitting {@code "citation": null}.
 */
public class CitationFormatterServiceLoaderTest {

  @Before
  public void reset() {
    // clear any formatter another test/base may have eagerly registered, and reset the
    // one-shot ServiceLoader guard so discovery runs fresh
    CitationFormatter.register(null);
    CitationFormatter.SERVICE_LOADER_TRIED[0] = false;
  }

  @After
  public void tearDown() {
    // do not leak our null/discovered state to other tests
    CitationFormatter.register(null);
    CitationFormatter.SERVICE_LOADER_TRIED[0] = false;
  }

  @Test
  public void autoDiscoversViaServiceLoader() {
    CitationFormatter cf = CitationFormatter.get();
    assertNotNull("CitationFormatter should be auto-discovered via ServiceLoader", cf);
    assertTrue("expected the citeproc-backed CslCitationFormatter, got " + cf.getClass(),
      cf instanceof CslCitationFormatter);
  }
}
