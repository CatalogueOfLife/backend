package life.catalogue.matching;

import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.model.SimpleNameClassified;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.nidx.NameIndex;

import org.gbif.nameparser.api.Rank;

import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * X in Y is the name of X, published in a work of Y, and is cited either way.
 * See https://github.com/CatalogueOfLife/backend/issues/1652
 */
public class UsageMatcherInAuthorTest {
  static final Map<String, Integer> NIDX = Map.of("Pipistrellus kuhlii", 10, "Aus bus", 20);
  UsageMatcher matcher;

  @Before
  public void init() {
    var store = new UsageMatcherMemStore(1);
    add(store, "pk", "Pipistrellus kuhlii", "(Kuhl, 1817)", TaxonomicStatus.ACCEPTED);
    add(store, "ab", "Aus bus", "Natterer in Kuhl, 1817", TaxonomicStatus.ACCEPTED);
    matcher = new UsageMatcher(1, mock(NameIndex.class), store, false);
  }

  static void add(UsageMatcherMemStore store, String id, String name, String authorship, TaxonomicStatus status) {
    var sn = new SimpleNameCached(id, name, Rank.SPECIES);
    sn.setAuthorship(authorship);
    sn.setStatus(status);
    sn.setNamesIndexId(NIDX.get(name));
    store.add(sn);
  }

  UsageMatch match(String name, String authorship) throws Exception {
    var q = new SimpleNameCached(null, name, Rank.SPECIES);
    q.setAuthorship(authorship);
    q.setStatus(TaxonomicStatus.ACCEPTED);
    q.setNamesIndexId(NIDX.get(name));
    return matcher.match(new SimpleNameClassified<>(q), false, false);
  }

  @Test
  public void inAuthors() throws Exception {
    var m = match("Pipistrellus kuhlii", "(Natterer in Kuhl, 1817)");
    assertEquals(MatchType.VARIANT, m.type);
    assertEquals("pk", m.usage.getId());
    // without the in citation nothing tells Natterer is Kuhl
    assertFalse(match("Pipistrellus kuhlii", "(Natterer, 1817)").isMatch());

    assertEquals("ab", match("Aus bus", "Kuhl, 1817").usage.getId());
    assertEquals("ab", match("Aus bus", "Natterer, 1817").usage.getId());
    // the year still has to agree, and a name of another author published in the same work is another name
    assertFalse(match("Aus bus", "Kuhl, 1850").isMatch());
    assertFalse(match("Aus bus", "Smith in Kuhl, 1817").isMatch());
  }
}
