package life.catalogue.es;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.BareName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.Synonym;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.search.NameUsageWrapper;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;

import co.elastic.clients.elasticsearch.core.SearchRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Integration test verifying that all three NameUsage subtypes round-trip through Elasticsearch
 * unchanged: index a NameUsageWrapper, read it back, and confirm it equals the original.
 */
public class NameUsageRoundTripIT extends EsTestBase {

  @Test
  public void testRoundTrip() throws IOException {
    NameUsageWrapper taxonWrapper = buildTaxonWrapper();
    NameUsageWrapper synonymWrapper = buildSynonymWrapper();
    NameUsageWrapper bareNameWrapper = buildBareNameWrapper();

    EsUtil.insert(client, cfg.index.name, taxonWrapper);
    EsUtil.insert(client, cfg.index.name, synonymWrapper);
    EsUtil.insert(client, cfg.index.name, bareNameWrapper);
    EsUtil.refreshIndex(client, cfg.index.name);

    SearchRequest req = SearchRequest.of(s -> s
        .index(cfg.index.name)
        .query(q -> q.matchAll(m -> m))
        .size(10));
    List<NameUsageWrapper> results = new EsQueryService(cfg.index.name, client).search(req);

    assertEquals(3, results.size());

    Map<Class<?>, NameUsageWrapper> byType = results.stream()
        .collect(Collectors.toMap(w -> w.getUsage().getClass(), w -> w));

    NameUsageWrapper taxonResult = byType.get(Taxon.class);
    NameUsageWrapper synonymResult = byType.get(Synonym.class);
    NameUsageWrapper bareNameResult = byType.get(BareName.class);

    assertNotNull("Taxon result not found", taxonResult);
    assertNotNull("Synonym result not found", synonymResult);
    assertNotNull("BareName result not found", bareNameResult);

    assertEquals(taxonWrapper, taxonResult);
    assertEquals(synonymWrapper, synonymResult);
    assertEquals(bareNameWrapper, bareNameResult);
  }

  private NameUsageWrapper buildTaxonWrapper() {
    Name n = TestEntityGenerator.newName("n1");
    Taxon t = TestEntityGenerator.newTaxon(n);
    return TestEntityGenerator.newNameUsageWrapper(t);
  }

  private NameUsageWrapper buildSynonymWrapper() {
    Name tn = TestEntityGenerator.newName("n2");
    Taxon t = TestEntityGenerator.newTaxon(tn);
    Name sn = TestEntityGenerator.newName("n3");
    var s = TestEntityGenerator.newSynonym(sn, t);
    return TestEntityGenerator.newNameUsageWrapper(s);
  }

  private NameUsageWrapper buildBareNameWrapper() {
    Name n = TestEntityGenerator.newName("bn1");
    // BareName.setId() is a no-op; the wrapper id will be null
    BareName bnu = new BareName(n);
    return new NameUsageWrapper(bnu);
  }

}
