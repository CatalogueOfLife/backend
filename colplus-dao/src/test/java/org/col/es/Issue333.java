package org.col.es;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.col.api.model.Name;
import org.col.api.model.Taxon;
import org.col.api.search.NameSearchRequest;
import org.col.api.search.NameSearchRequest.SearchContent;
import org.col.api.search.NameUsageWrapper;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * https://github.com/Sp2000/colplus-backend/issues/333
 */
public class Issue333 extends EsReadTestBase {

  @Before
  public void before() throws IOException {
    EsUtil.deleteIndex(getEsClient(), indexName);
    EsUtil.createIndex(getEsClient(), indexName, getEsConfig().nameUsage);
  }

  @Test
  public void test1() {
    index(createTestObjects());
    NameSearchRequest query = new NameSearchRequest();
    // This was said to only return the binomial, but not the trinomial.
    query.setContent(EnumSet.of(SearchContent.SCIENTIFIC_NAME));
    query.setQ("trilineatu");
    assertEquals(createTestObjects(), search(query).getResult());
  }

  private List<NameUsageWrapper> createTestObjects() {
    Name n = new Name();
    n.setScientificName("Leptoiulus trilineatus nigra");
    Taxon t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw1 = new NameUsageWrapper(t);

    n = new Name();
    n.setScientificName("Leptoiulus trilineatus");
    t = new Taxon();
    t.setName(n);
    NameUsageWrapper nuw2 = new NameUsageWrapper(t);

    return Arrays.asList(nuw1, nuw2);
  }

}
